package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.co.bns.warehouse_api.dto.GdmsSyncLogResponse;
import uk.co.bns.warehouse_api.entity.GdmsSyncLog;
import uk.co.bns.warehouse_api.repository.GdmsSyncLogRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Records one row per MAC per GDMS assign/recall attempt - GdmsChannelService
 * is the only writer, immediately after each /assign or /recycle batch call
 * comes back (or fails outright). Deliberately swallows its own failures
 * (falls back to a warn log) rather than throwing, since a logging problem
 * must never take down the actual GDMS call or the stock operation that
 * triggered it - worst case, that one attempt just isn't visible on the log
 * page.
 */
@Service
@RequiredArgsConstructor
public class GdmsSyncLogService {

    private static final Logger log = LoggerFactory.getLogger(GdmsSyncLogService.class);

    private final GdmsSyncLogRepository gdmsSyncLogRepository;

    public void recordSuccess(String operation, String source, String orderNumber, String mac,
                               String channelId, String channelName) {
        record(operation, source, orderNumber, mac, channelId, channelName, "SUCCESS", null);
    }

    public void recordFailure(String operation, String source, String orderNumber, String mac,
                               String channelId, String channelName, String errorReason) {
        record(operation, source, orderNumber, mac, channelId, channelName, "FAILURE", errorReason);
    }

    private void record(String operation, String source, String orderNumber, String mac,
                         String channelId, String channelName, String status, String errorReason) {
        try {
            GdmsSyncLog entry = new GdmsSyncLog();
            entry.setAttemptedAt(LocalDateTime.now());
            entry.setOperation(operation);
            entry.setSource(source);
            entry.setOrderNumber(orderNumber);
            entry.setMacAddress(mac);
            entry.setChannelId(channelId);
            entry.setChannelName(channelName);
            entry.setStatus(status);
            entry.setErrorReason(errorReason != null && errorReason.length() > 500
                    ? errorReason.substring(0, 500) : errorReason);
            gdmsSyncLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("Failed to write GDMS sync log row for MAC {}: {}", mac, e.getMessage());
        }
    }

    public List<GdmsSyncLogResponse> listAll() {
        return gdmsSyncLogRepository.findAllByOrderByAttemptedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    private GdmsSyncLogResponse toResponse(GdmsSyncLog e) {
        return new GdmsSyncLogResponse(e.getId(), e.getAttemptedAt(), e.getOperation(), e.getSource(),
                e.getOrderNumber(), e.getMacAddress(), e.getChannelId(), e.getChannelName(),
                e.getStatus(), e.getErrorReason());
    }
}

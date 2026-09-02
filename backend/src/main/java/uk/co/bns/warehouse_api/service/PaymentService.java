package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.bns.warehouse_api.dto.PaymentRequest;
import uk.co.bns.warehouse_api.dto.PaymentView;
import uk.co.bns.warehouse_api.entity.Order;
import uk.co.bns.warehouse_api.entity.Payment;
import uk.co.bns.warehouse_api.repository.PaymentRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderService orderService;

    @Transactional
    public Payment record(Long orderId, PaymentRequest request) {
        Order order = orderService.findById(orderId);
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(request.amount());
        payment.setReceivedAt(request.receivedAt() != null ? request.receivedAt() : LocalDateTime.now());
        payment.setReference(request.reference());
        payment.setNotes(request.notes());
        payment.setRecordedBy(request.recordedBy());
        return paymentRepository.save(payment);
    }

    public List<Payment> findByOrder(Long orderId) {
        return paymentRepository.findByOrder_IdOrderByReceivedAtDesc(orderId);
    }

    public PaymentView toView(Payment payment) {
        return new PaymentView(payment.getId(), payment.getOrder().getId(), payment.getOrder().getOrderNumber(),
                payment.getAmount(), payment.getReceivedAt(), payment.getReference(), payment.getNotes(),
                payment.getRecordedBy());
    }
}

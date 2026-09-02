package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.co.bns.warehouse_api.entity.User;
import uk.co.bns.warehouse_api.entity.UserSetting;
import uk.co.bns.warehouse_api.exception.NotFoundException;
import uk.co.bns.warehouse_api.repository.UserRepository;
import uk.co.bns.warehouse_api.repository.UserSettingRepository;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserSettingsService {

    private final UserSettingRepository userSettingRepository;
    private final UserRepository userRepository;

    public Map<String, String> getForUser(String userName) {
        User user = userRepository.findByName(userName)
                .orElseThrow(() -> new NotFoundException("User not found"));
        Map<String, String> map = new LinkedHashMap<>();
        for (UserSetting s : userSettingRepository.findByUser_Id(user.getId())) {
            map.put(s.getSettingKey(), s.getSettingValue());
        }
        return map;
    }

    public void setAllForUser(String userName, Map<String, String> values) {
        User user = userRepository.findByName(userName)
                .orElseThrow(() -> new NotFoundException("User not found"));
        values.forEach((key, value) -> {
            UserSetting setting = userSettingRepository.findByUser_IdAndSettingKey(user.getId(), key)
                    .orElseGet(() -> {
                        UserSetting s = new UserSetting();
                        s.setUser(user);
                        s.setSettingKey(key);
                        return s;
                    });
            setting.setSettingValue(value);
            userSettingRepository.save(setting);
        });
    }
}

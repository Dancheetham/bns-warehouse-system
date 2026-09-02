package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.co.bns.warehouse_api.entity.AppSetting;
import uk.co.bns.warehouse_api.repository.AppSettingRepository;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final AppSettingRepository appSettingRepository;

    public Map<String, String> getAll() {
        Map<String, String> map = new LinkedHashMap<>();
        for (AppSetting s : appSettingRepository.findAll()) {
            map.put(s.getSettingKey(), s.getSettingValue());
        }
        return map;
    }

    public String get(String key, String defaultValue) {
        return appSettingRepository.findById(key)
                .map(AppSetting::getSettingValue)
                .filter(v -> v != null && !v.isBlank())
                .orElse(defaultValue);
    }

    public void set(String key, String value) {
        AppSetting setting = appSettingRepository.findById(key).orElse(new AppSetting(key, value));
        setting.setSettingValue(value);
        appSettingRepository.save(setting);
    }

    public void setAll(Map<String, String> values) {
        values.forEach(this::set);
    }
}

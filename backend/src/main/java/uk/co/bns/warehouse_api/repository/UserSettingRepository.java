package uk.co.bns.warehouse_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.bns.warehouse_api.entity.UserSetting;

import java.util.List;
import java.util.Optional;

public interface UserSettingRepository extends JpaRepository<UserSetting, Long> {
    List<UserSetting> findByUser_Id(Long userId);
    Optional<UserSetting> findByUser_IdAndSettingKey(Long userId, String settingKey);
}

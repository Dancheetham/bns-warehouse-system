CREATE TABLE app_settings (
    setting_key VARCHAR(100) PRIMARY KEY,
    setting_value VARCHAR(500)
);

INSERT INTO app_settings (setting_key, setting_value) VALUES
    ('picking_note_printer', ''),
    ('print_agent_url', 'http://localhost:9191/print'),
    ('auto_acknowledge_on_release', 'true');

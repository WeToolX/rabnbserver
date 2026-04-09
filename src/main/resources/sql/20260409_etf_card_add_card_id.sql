ALTER TABLE etf_card
    ADD COLUMN card_id INT NOT NULL DEFAULT 1 COMMENT '卡牌类型ID 1/2/3' AFTER id;

ALTER TABLE etf_card
    ADD INDEX idx_etf_card_card_id (card_id),
    ADD INDEX idx_etf_card_card_active_status (card_id, is_current, status);

UPDATE etf_card
SET card_id = 1
WHERE card_id IS NULL OR card_id = 0;

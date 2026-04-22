ALTER TABLE `user`
    MODIFY COLUMN user_grade INT DEFAULT 0 COMMENT '自动用户等级, 0表示无等级';

UPDATE `user`
SET user_grade = 0
WHERE user_grade IS NULL OR user_grade < 0;

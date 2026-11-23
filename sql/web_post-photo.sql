SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='web_post' AND column_name='photo'),'SELECT 1','ALTER TABLE web_post ADD COLUMN photo MEDIUMTEXT NULL');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE task (
id CHAR(24) PRIMARY KEY,
status VARCHAR(64) NOT NULL DEFAULT "initial",
title VARCHAR(128) NOT NULL,
description VARCHAR(512),
assigned_to CHAR(24),
estimation INT,
created_by CHAR(24) NOT NULL,
updated_by CHAR(24),
created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
updated_at TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
FOREIGN KEY (assigned_to) REFERENCES user(id),
FOREIGN KEY (created_by) REFERENCES user(id),
FOREIGN KEY (updated_by) REFERENCES user(id)
);

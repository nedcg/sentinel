CREATE TABLE priority (
task_id CHAR(24),
user_id CHAR(24),
priority_global INT NOT NULL DEFAULT 0,
priority_user INT NOT NULL,
created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
PRIMARY KEY (task_id, user_id),
FOREIGN KEY (task_id) REFERENCES task(id),
FOREIGN KEY (user_id) REFERENCES user(id),
UNIQUE (user_id, priority_user)
);

CREATE TABLE task_history (
task_id CHAR(24),
id CHAR(24),
type VARCHAR(64),
fields JSON,
created_by CHAR(24) NOT NULL,
created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
PRIMARY KEY (task_id, id),
FOREIGN KEY (task_id) REFERENCES task(id),
FOREIGN KEY (created_by) REFERENCES user(id)
);

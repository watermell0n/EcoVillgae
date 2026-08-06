ALTER TABLE activity_sessions
    ADD COLUMN presentation_image_url text;

ALTER TABLE activity_sessions
    ADD COLUMN presentation_title varchar(120);

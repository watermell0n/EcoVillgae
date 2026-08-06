ALTER TABLE classrooms
    ADD COLUMN display_name varchar(120),
    ADD COLUMN class_number integer,
    ADD COLUMN description varchar(500);

UPDATE classrooms
   SET display_name = grade || '학년 경제교실'
 WHERE display_name IS NULL;

ALTER TABLE classrooms
    ALTER COLUMN display_name SET NOT NULL,
    ADD CONSTRAINT ck_classrooms_class_number
        CHECK (class_number IS NULL OR class_number BETWEEN 1 AND 99);

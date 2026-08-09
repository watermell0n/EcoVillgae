-- Keep historical property rows for closed contracts while showing only the current seat-board generation.
ALTER TABLE property_layouts
    ADD COLUMN layout_generation bigint NOT NULL DEFAULT 1;

ALTER TABLE properties
    ADD COLUMN layout_generation bigint NOT NULL DEFAULT 1;

ALTER TABLE properties
    ADD COLUMN rent_deposit_amount bigint NOT NULL DEFAULT 0;

-- A board may already have been rebuilt before this migration. Those old rows were
-- marked inactive but were still returned to teachers. The last layout update time
-- is also the creation time used for the current generated cells, so older rows can
-- safely be assigned to the historical generation.
UPDATE properties p
   SET layout_generation = 0
  FROM property_layouts l
 WHERE p.classroom_id = l.classroom_id
   AND p.created_at < l.updated_at;

-- Existing classrooms used one deposit value for both jeonse and monthly rent.
-- Copy it so deploying this split does not silently change an already published rent contract condition.
UPDATE properties
   SET rent_deposit_amount = deposit_amount;

ALTER TABLE properties
    ADD CONSTRAINT ck_properties_rent_deposit_amount
    CHECK (rent_deposit_amount >= 0);

CREATE INDEX ix_properties_current_layout
    ON properties (classroom_id, layout_generation, row_index, column_index);

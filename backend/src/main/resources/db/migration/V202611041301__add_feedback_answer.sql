ALTER TABLE feedbacks
    ADD COLUMN answer_content TEXT,
    ADD COLUMN answered_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_feedbacks_answer_pair
        CHECK ((answer_content IS NULL) = (answered_at IS NULL));

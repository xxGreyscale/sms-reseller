-- V7: Add campaign_contact_ids collection table for flat-contact campaign targeting (D-12, MOBL-07).
--
-- Mirrors campaign_groups in structure. Either campaign_groups or campaign_contact_ids
-- is populated per campaign (never both, never both empty — enforced at service layer).
--
-- contact_id is a logical reference to the contact-service contacts table (no FK —
-- cross-service reference, consistent with campaign_groups / group_id pattern).

CREATE TABLE campaign_contact_ids (
    campaign_id UUID NOT NULL REFERENCES campaigns (id) ON DELETE CASCADE,
    contact_id  UUID NOT NULL,
    PRIMARY KEY (campaign_id, contact_id)
);

CREATE INDEX idx_campaign_contact_ids_campaign
    ON campaign_contact_ids (campaign_id);

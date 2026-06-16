
const databaseName = process.env.MONGO_DATABASE
  || process.env.MONGO_INITDB_DATABASE
  || "kachi_notification";

const database = db.getSiblingDB(databaseName);

database.notifications.createIndex(
  { requestId: 1 },
  {
    name: "ux_notifications_request_id",
    unique: true,
    background: true
  }
);

database.notifications.createIndex(
  { status: 1, updatedAt: 1 },
  {
    name: "idx_notifications_status_updated_at",
    background: true
  }
);

database.notifications.createIndex(
  { status: 1, claimedAt: 1 },
  {
    name: "idx_notifications_stuck_processing",
    background: true
  }
);

database.notification_outboxes.createIndex(
  { outboxStatus: 1, nextRetryAt: 1, createdAt: 1 },
  {
    name: "idx_notification_outboxes_publishable",
    background: true
  }
);

database.notification_outboxes.createIndex(
  { outboxStatus: 1, claimedAt: 1 },
  {
    name: "idx_notification_outboxes_stale_publishing",
    background: true
  }
);

database.notification_outboxes.createIndex(
  { notificationId: 1 },
  {
    name: "idx_notification_outboxes_notification_id",
    background: true
  }
);

// notification-routing 데이터베이스 인덱스.
// eventKey unique는 같은 ai 이벤트의 이중 라우팅을 막는 근거이며 RoutingJobConflictException이 이 제약에 의존한다.

const databaseName = process.env.MONGO_ROUTING_DATABASE
  || "kachi_notification_routing";

const database = db.getSiblingDB(databaseName);

database.routing_jobs.createIndex(
  { eventKey: 1 },
  {
    name: "ux_routing_jobs_event_key",
    unique: true,
    background: true
  }
);

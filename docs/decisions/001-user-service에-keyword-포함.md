# 001. user-service에 Keyword 포함

## 배경

Kachi에서 Keyword는 전역 키워드 사전이 아니라 사용자가 등록한 관심 주제다. 키워드 등록, 수정, 삭제 가능 여부는 사용자 상태에 영향을 받는다.

예를 들어 탈퇴한 사용자는 키워드를 수정할 수 없고, 비활성 키워드는 수집 대상에서 제외된다.

## 결정

Keyword는 `user-service` 안에서 관리한다.

패키지는 다음 구조를 기본으로 한다.

```text
me.rgunny.kachi.user
├── domain
├── application
│   ├── port
│   │   ├── in
│   │   └── out
│   └── service
├── adapter
│   ├── in
│   │   └── web
│   └── out
│       └── persistence
└── config
```

## 결과

- `user-service`는 사용자와 사용자 관심 키워드의 정합성을 함께 관리한다.
- `collector-service`는 키워드를 직접 수정하지 않는다.
- `collector-service`가 수집 대상 키워드가 필요하면 `user-service` API, 이벤트, 또는 별도 동기화 모델을 통해 받는다.
- 키워드가 나중에 전역 트렌드, 추천, 검색 사전 도메인으로 커지면 별도 context 분리를 다시 검토한다.

# Fake druid application for course summary

To setup the application
```
npm install
```

Run the application
```
node index.js
```

## Request

### Request URL:
```
POST http://localhost:8081/druid/v2/
```

### Request body

```
{
  "queryType": "groupBy",
  "dataSource": "audit-rollup-syncts",
  "dimensions": [
    "edata_type",
    {"type":"extraction","dimension":"derived_loc_state","outputName":"state","extractionFn":{"type":"registeredLookup","lookup":"stateLookup","retainMissingValue":true}},
    {"type":"extraction","dimension":"derived_loc_district","outputName":"district","extractionFn":{"type":"registeredLookup","lookup":"districtLookup","retainMissingValue":true}}
  ],
  "aggregations": [
    {
      "fieldName": "actor_id",
      "fieldNames": [
        "actor_id"
      ],
      "type": "cardinality",
      "name": "userCount"
    }
  ],
  "granularity": "all",
  "postAggregations": [],
  "intervals": "2020-01-01/2024-01-01",
  "filter": {
    "type": "and",
    "fields": [
      {
        "type": "or",
        "fields": [
          {
            "type": "selector",
            "dimension": "edata_type",
            "value": "enrol"
          },
          {
            "type": "selector",
            "dimension": "edata_type",
            "value": "certificate-issued"
          }
        ]
      },
      {
        "type": "and",
        "fields": [
          {
            "type": "selector",
            "dimension": "context_cdata_id",
            "value": "0134418070901473284"
          },
          {
            "type": "and",
            "fields": [
              {
                "type": "selector",
                "dimension": "object_rollup_l1",
                "value": "do_2133705102934917121211"
              },
              {
                "type": "selector",
                "dimension": "eid",
                "value": "AUDIT"
              }
            ]
          }
        ]
      }
    ]
  },
  "limitSpec": {
    "type": "default",
    "limit": 10000,
    "columns": [
      {
        "dimension": "userCount",
        "direction": "descending"
      }
    ]
  }
}
```

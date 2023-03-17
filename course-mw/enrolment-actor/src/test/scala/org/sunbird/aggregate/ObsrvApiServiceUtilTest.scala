package org.sunbird.aggregate
import okhttp3.mockwebserver.{MockResponse, MockWebServer}
import org.scalatest.{FlatSpec, Matchers}
import java.util
import org.sunbird.common.util.JsonUtil

import java.io.IOException

class ObsrvApiServiceUtilTest extends FlatSpec with Matchers{

  "An ObsrvApiServiceUtil" should "call the Obsrv API service correctly" in {
    val util = new ObsrvApiServiceUtil
    val collectionSummaryAggregateTest = new CollectionSummaryAggregateTest
    collectionSummaryAggregateTest.mockDruid("[{\"version\":\"v1\",\"timestamp\":\"1901-01-01T00:00:00.000Z\",\"event\":{\"district\":null,\"userCount\":2.000977198748901,\"edata_type\":\"enrol\",\"state\":null}},{\"version\":\"v1\",\"timestamp\":\"1901-01-01T00:00:00.000Z\",\"event\":{\"district\":null,\"userCount\":1.0002442201269182,\"edata_type\":\"complete\",\"state\":null}},{\"version\":\"v1\",\"timestamp\":\"1901-01-01T00:00:00.000Z\",\"event\":{\"district\":\"PUNE\",\"userCount\":1.0002442201269182,\"edata_type\":\"enrol\",\"state\":\"Maharashtra\"}},{\"version\":\"v1\",\"timestamp\":\"1901-01-01T00:00:00.000Z\",\"event\":{\"district\":\"Tumkur\",\"userCount\":1.0002442201269182,\"edata_type\":\"enrol\",\"state\":\"Karnataka\"}},{\"version\":\"v1\",\"timestamp\":\"1901-01-01T00:00:00.000Z\",\"event\":{\"district\":\"DADRA AND NAGAR HAVELI(UT)\",\"userCount\":1.0002442201269182,\"edata_type\":\"enrol\",\"state\":\"Dadra & Nagar Haveli\"}},{\"version\":\"v1\",\"timestamp\":\"1901-01-01T00:00:00.000Z\",\"event\":{\"district\":\"PUNE\",\"userCount\":1.0002442201269182,\"edata_type\":\"complete\",\"state\":\"Maharashtra\"}},{\"version\":\"v1\",\"timestamp\":\"1901-01-01T00:00:00.000Z\",\"event\":{\"district\":\"DADRA AND NAGAR HAVELI(UT)\",\"userCount\":1.0002442201269182,\"edata_type\":\"complete\",\"state\":\"Dadra & Nagar Haveli\"}}]")
    val query = "{\"request\":{\"filters\":{\"collectionId\":\"do_31309287232935526411138\",\"batchId\":\"0130929928739635201\"},\"groupBy\":[],\"intervals\":\"20120-01-23/2020-09-24\"}}"
    val result = util.callObsrvService(JsonUtil.serialize(query))
    result shouldNot be(null)
  }
}

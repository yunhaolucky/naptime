package org.coursera.naptime.ari.engine

import com.google.inject.Injector
import com.linkedin.data.DataList
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ari.FetcherApi
import org.coursera.naptime.ari.Request
import org.coursera.naptime.ari.RequestField
import org.coursera.naptime.ari.Response
import org.coursera.naptime.ari.TopLevelRequest
import org.coursera.naptime.ari.graphql.models.MergedCourse
import org.coursera.naptime.ari.graphql.models.MergedInstructor
import org.coursera.naptime.model.Keyed
import org.coursera.naptime.router2.NaptimeRoutes
import org.coursera.naptime.router2.ResourceRouterBuilder
import org.coursera.naptime.schema.Resource
import org.coursera.naptime.schema.ResourceKind
import org.junit.Test
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.JsString
import play.api.test.FakeRequest

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class EngineImplTest extends AssertionsForJUnit with ScalaFutures with MockitoSugar {

  import EngineImplTest._

  val fetcherApi = mock[FetcherApi]

  val extraTypes = TYPE_SCHEMAS.map { case (key, value) => Keyed(key, value) }.toList

  val courseRouterBuilder = mock[ResourceRouterBuilder]
  when(courseRouterBuilder.schema).thenReturn(COURSES_RESOURCE)
  when(courseRouterBuilder.types).thenReturn(extraTypes)


  val instructorRouterBuilder = mock[ResourceRouterBuilder]
  when(instructorRouterBuilder.schema).thenReturn(INSTRUCTORS_RESOURCE)
  when(instructorRouterBuilder.types).thenReturn(extraTypes)

  val injector = mock[Injector]
  val naptimeRoutes = NaptimeRoutes(injector, Set(courseRouterBuilder, instructorRouterBuilder))
  val engine = new EngineImpl(naptimeRoutes, fetcherApi)

  @Test
  def singleResourceFetch_Courses(): Unit = {
    val request = Request(
      requestHeader = FakeRequest(),
      topLevelRequests = List(TopLevelRequest(
        resource = COURSES_RESOURCE_ID,
        selection = RequestField(
          name = "CoursesV1",
          alias = None,
          args = Set("id" -> JsString(COURSE_A.id)),
          selections = List(
            RequestField("id", None, Set.empty, List.empty),
            RequestField("slug", None, Set.empty, List.empty),
            RequestField("name", None, Set.empty, List.empty))))))

    val topLevelDataList = new DataList()
    topLevelDataList.add(COURSE_A.id)
    val fetcherResponse = Response(
      topLevelIds = Map(request.topLevelRequests.head -> topLevelDataList),
      data = Map(COURSES_RESOURCE_ID -> Map(
        COURSE_A.id -> COURSE_A.data())))

    when(fetcherApi.data(request)).thenReturn(Future.successful(fetcherResponse))

    val result = engine.execute(request).futureValue

    assert(result.topLevelIds.contains(request.topLevelRequests.head))
    assert(1 === result.topLevelIds(request.topLevelRequests.head).size())
    assert(COURSE_A.id === result.topLevelIds(request.topLevelRequests.head).get(0))
    assert(result.data.contains(COURSES_RESOURCE_ID))
    val coursesData = result.data(COURSES_RESOURCE_ID)
    assert(1 === coursesData.size)
    assert(coursesData.contains(COURSE_A.id))
    val courseAResponse = coursesData(COURSE_A.id)
    assert(COURSE_A.id === courseAResponse.getString("id"))
    assert(COURSE_A.name === courseAResponse.getString("name"))
    assert(COURSE_A.slug === courseAResponse.getString("slug"))
  }

  @Test
  def singleResourceFetch_Instructors(): Unit = {
    val request = Request(
      requestHeader = FakeRequest(),
      topLevelRequests = List(TopLevelRequest(
        resource = INSTRUCTORS_RESOURCE_ID,
        selection = RequestField(
          name = "InstructorsV1",
          alias = None,
          args = Set("id" -> JsString(INSTRUCTOR_1.id)),
          selections = List(
            RequestField("id", None, Set.empty, List.empty),
            RequestField("name", None, Set.empty, List.empty),
            RequestField("title", None, Set.empty, List.empty))))))

    val topLevelDataList = new DataList()
    topLevelDataList.add(INSTRUCTOR_1.id)
    val fetcherResponse = Response(
      topLevelIds = Map(request.topLevelRequests.head -> topLevelDataList),
      data = Map(INSTRUCTORS_RESOURCE_ID -> Map(
        INSTRUCTOR_1.id -> INSTRUCTOR_1.data())))

    when(fetcherApi.data(request)).thenReturn(Future.successful(fetcherResponse))

    val result = engine.execute(request).futureValue

    assert(result.topLevelIds.contains(request.topLevelRequests.head))
    assert(1 === result.topLevelIds(request.topLevelRequests.head).size())
    assert(INSTRUCTOR_1.id === result.topLevelIds(request.topLevelRequests.head).get(0))
    assert(result.data.contains(INSTRUCTORS_RESOURCE_ID))
    val instructorsData = result.data(INSTRUCTORS_RESOURCE_ID)
    assert(1 === instructorsData.size)
    assert(instructorsData.contains(INSTRUCTOR_1.id))
    val instructor1Response = instructorsData(INSTRUCTOR_1.id)
    assert(INSTRUCTOR_1.id === instructor1Response.getString("id"))
    assert(INSTRUCTOR_1.name === instructor1Response.getString("name"))
    assert(INSTRUCTOR_1.title === instructor1Response.getString("title"))
  }

  // TODO: Check pagination.

  // TODO: Add sophisticated tests that involve joining resources.

  // TODO: Add invalid schema-based tests.

  /**
   * Runs 2 simple top level requests for independent resources, and ensures the response is appropriately merged.
   */
  @Test
  def multiResourceFetch(): Unit = {
    val request = Request(
      requestHeader = FakeRequest(),
      topLevelRequests = List(
        TopLevelRequest(
          resource = COURSES_RESOURCE_ID,
          selection = RequestField(
            name = "get",
            alias = None,
            args = Set("id" -> JsString(COURSE_A.id)),
            selections = List(
              RequestField("id", None, Set.empty, List.empty),
              RequestField("slug", None, Set.empty, List.empty),
              RequestField("name", None, Set.empty, List.empty)))),
        TopLevelRequest(
          resource = INSTRUCTORS_RESOURCE_ID,
          selection = RequestField(
            name = "InstructorsV1",
            alias = None,
            args = Set("id" -> JsString(INSTRUCTOR_1.id)),
            selections = List(
              RequestField("id", None, Set.empty, List.empty),
              RequestField("name", None, Set.empty, List.empty),
              RequestField("title", None, Set.empty, List.empty))))))

    val topLevelDataListCourse = new DataList(List(COURSE_A.id).asJava)
    val fetcherResponseCourse = Response(
      topLevelIds = Map(request.topLevelRequests.head -> topLevelDataListCourse),
      data = Map(COURSES_RESOURCE_ID -> Map(
        COURSE_A.id -> COURSE_A.data())))
    val topLevelDataListInstructor = new DataList()
    topLevelDataListInstructor.add(INSTRUCTOR_1.id)
    val fetcherResponseInstructors = Response(
      topLevelIds = Map(request.topLevelRequests.tail.head -> topLevelDataListInstructor),
      data = Map(INSTRUCTORS_RESOURCE_ID -> Map(
        INSTRUCTOR_1.id -> INSTRUCTOR_1.data())))

    when(fetcherApi.data(any())).thenReturn(
      Future.successful(fetcherResponseCourse), Future.successful(fetcherResponseInstructors))

    val result = engine.execute(request).futureValue

    assert(result.topLevelIds.contains(request.topLevelRequests.head))
    assert(1 === result.topLevelIds(request.topLevelRequests.head).size())
    assert(COURSE_A.id === result.topLevelIds(request.topLevelRequests.head).get(0))
    assert(result.data.contains(COURSES_RESOURCE_ID))
    val coursesData = result.data(COURSES_RESOURCE_ID)
    assert(1 === coursesData.size)
    assert(coursesData.contains(COURSE_A.id))
    val courseAResponse = coursesData(COURSE_A.id)
    assert(COURSE_A.id === courseAResponse.getString("id"))
    assert(COURSE_A.name === courseAResponse.getString("name"))
    assert(COURSE_A.slug === courseAResponse.getString("slug"))

    assert(result.topLevelIds.contains(request.topLevelRequests.head))
    assert(1 === result.topLevelIds(request.topLevelRequests.head).size())
    assert(INSTRUCTOR_1.id === result.topLevelIds(request.topLevelRequests.tail.head).get(0))
    assert(result.data.contains(INSTRUCTORS_RESOURCE_ID))
    val instructorsData = result.data(INSTRUCTORS_RESOURCE_ID)
    assert(1 === instructorsData.size)
    assert(instructorsData.contains(INSTRUCTOR_1.id))
    val instructor1Response = instructorsData(INSTRUCTOR_1.id)
    assert(INSTRUCTOR_1.id === instructor1Response.getString("id"))
    assert(INSTRUCTOR_1.name === instructor1Response.getString("name"))
    assert(INSTRUCTOR_1.title === instructor1Response.getString("title"))
  }
}

object EngineImplTest {
  val COURSE_A = MergedCourse(
    id = "courseAId",
    name = "Machine Learning",
    slug = "machine-learning",
    description = Some("An awesome course on machine learning."),
    instructors = List("instructor1Id"),
    originalId = "")

  val INSTRUCTOR_1 = MergedInstructor(
    id = "instructor1Id",
    name = "Professor X",
    title = "Chair",
    bio = "Professor X's bio",
    courses = List(COURSE_A.id))

  val COURSES_RESOURCE_ID = ResourceName("courses", 1)
  val COURSES_RESOURCE = Resource(
    kind = ResourceKind.COLLECTION,
    name = "courses",
    version = Some(1),
    parentClass = None,
    keyType = "string",
    valueType = "org.coursera.naptime.test.Course",
    mergedType = MergedCourse.SCHEMA.getFullName,
    handlers = List.empty,
    className = "org.coursera.naptime.test.CoursesResource",
    attributes = List.empty)

  val INSTRUCTORS_RESOURCE_ID = ResourceName("instructors", 1)
  val INSTRUCTORS_RESOURCE = Resource(
    kind = ResourceKind.COLLECTION,
    name = INSTRUCTORS_RESOURCE_ID.topLevelName,
    version = Some(INSTRUCTORS_RESOURCE_ID.version),
    parentClass = None,
    keyType = "string",
    valueType = "org.coursera.naptime.test.Instructor",
    mergedType = "org.coursera.naptime.test.InstructorsResourceModel",
    handlers = List.empty,
    className = "org.coursera.naptime.test.InstructorsResource",
    attributes = List.empty)

  val RESOURCE_SCHEMAS = Seq(
    COURSES_RESOURCE,
    INSTRUCTORS_RESOURCE)

  val TYPE_SCHEMAS = Map(
    MergedCourse.SCHEMA.getFullName -> MergedCourse.SCHEMA)
}
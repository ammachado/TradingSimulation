import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, mvc}
import play.api.test._
import play.api.test.Helpers._

import scala.concurrent.Future

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
@RunWith(classOf[JUnitRunner])
class ApplicationSpec extends Specification {

  val application: Application = new GuiceApplicationBuilder()
    .configure("some.configuration" -> "value")
    .build()

  "Application" should {

    "send 404 on a bad request" in {
      val result: Future[mvc.Result] = route(application, FakeRequest(GET, "/boum")).get

      status(result) must equalTo(NOT_FOUND)
    }

    "render the index page" in {
      val home: Future[mvc.Result] = route(application, FakeRequest(GET, "/")).get

      status(home) must equalTo(OK)
      contentType(home) must beSome.which(_ == "text/html")
    }
  }
}

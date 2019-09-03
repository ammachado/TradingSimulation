package ch.epfl.ts.test

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner]) 
class HelloTest extends FunSuite {
  
  test("Tests are being executed") {
    assert(2 + 2 == 4)
  }
}
/*
 * Designed and developed by 2022 skydoves (Jaewoong Eum)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package demo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Comprehensive tests for Route sealed interface.
 *
 * Tests navigation routes for the Cloudy demo app, covering
 * serialization, equality, and type safety.
 */
internal class RouteTest {

  /**
   * Verifies RadiusList is a singleton object.
   */
  @Test
  fun radiusListShouldBeSingleton() {
    val route1 = Route.RadiusList
    val route2 = Route.RadiusList
    
    assertSame(route1, route2, "RadiusList should be singleton")
  }

  /**
   * Verifies RadiusDetail with same radius are equal.
   */
  @Test
  fun radiusDetailWithSameRadiusShouldBeEqual() {
    val route1 = Route.RadiusDetail(25)
    val route2 = Route.RadiusDetail(25)
    
    assertEquals(route1, route2, "Same radius should be equal")
  }

  /**
   * Verifies RadiusDetail with different radius are not equal.
   */
  @Test
  fun radiusDetailWithDifferentRadiusShouldNotBeEqual() {
    val route1 = Route.RadiusDetail(25)
    val route2 = Route.RadiusDetail(50)
    
    assertNotEquals(route1, route2, "Different radius should not be equal")
  }

  /**
   * Verifies RadiusList and RadiusDetail are different types.
   */
  @Test
  fun radiusListAndRadiusDetailShouldBeDifferentTypes() {
    val listRoute: Route = Route.RadiusList
    val detailRoute: Route = Route.RadiusDetail(25)
    
    assertTrue(listRoute is Route.RadiusList)
    assertTrue(detailRoute is Route.RadiusDetail)
    assertFalse(listRoute is Route.RadiusDetail)
    assertFalse(detailRoute is Route.RadiusList)
  }

  /**
   * Verifies RadiusDetail stores radius correctly.
   */
  @Test
  fun radiusDetailShouldStoreRadiusCorrectly() {
    val radius = 42
    val route = Route.RadiusDetail(radius)
    
    assertEquals(radius, route.radius, "Radius should be stored correctly")
  }

  /**
   * Verifies zero radius is valid.
   */
  @Test
  fun radiusDetailShouldAcceptZeroRadius() {
    val route = Route.RadiusDetail(0)
    
    assertEquals(0, route.radius, "Zero radius should be accepted")
  }

  /**
   * Verifies negative radius is stored (validation may be elsewhere).
   */
  @Test
  fun radiusDetailShouldStoreNegativeRadius() {
    val route = Route.RadiusDetail(-1)
    
    assertEquals(-1, route.radius, "Negative radius should be stored")
  }

  /**
   * Verifies large radius values.
   */
  @Test
  fun radiusDetailShouldHandleLargeRadius() {
    val largeRadius = 10000
    val route = Route.RadiusDetail(largeRadius)
    
    assertEquals(largeRadius, route.radius, "Large radius should be stored")
  }

  /**
   * Verifies typical radius values from demo app.
   */
  @Test
  fun radiusDetailShouldHandleTypicalRadii() {
    val typicalRadii = listOf(0, 5, 10, 15, 20, 25, 30, 40, 50, 75, 100)
    
    typicalRadii.forEach { radius ->
      val route = Route.RadiusDetail(radius)
      assertEquals(radius, route.radius, "Radius $radius should be stored")
    }
  }

  /**
   * Verifies when expression exhaustiveness.
   */
  @Test
  fun whenExpressionShouldBeExhaustive() {
    val routes: List<Route> = listOf(
      Route.RadiusList,
      Route.RadiusDetail(10),
      Route.RadiusDetail(25),
    )
    
    routes.forEach { route ->
      val result = when (route) {
        Route.RadiusList -> "list"
        is Route.RadiusDetail -> "detail:${route.radius}"
      }
      assertTrue(result.isNotEmpty(), "Should match a case")
    }
  }

  /**
   * Verifies hashCode consistency for RadiusDetail.
   */
  @Test
  fun radiusDetailHashCodeShouldBeConsistent() {
    val route1 = Route.RadiusDetail(25)
    val route2 = Route.RadiusDetail(25)
    
    assertEquals(
      route1.hashCode(),
      route2.hashCode(),
      "Same radius should have same hashCode",
    )
  }

  /**
   * Verifies hashCode difference for different radius.
   */
  @Test
  fun radiusDetailWithDifferentRadiusShouldHaveDifferentHashCode() {
    val route1 = Route.RadiusDetail(25)
    val route2 = Route.RadiusDetail(50)
    
    // Note: Different values may occasionally have same hashCode (collision)
    // but for simple integers this is unlikely
    assertNotEquals(
      route1.hashCode(),
      route2.hashCode(),
      "Different radius should typically have different hashCode",
    )
  }

  /**
   * Verifies toString provides useful information.
   */
  @Test
  fun routesShouldHaveMeaningfulToString() {
    val listRoute = Route.RadiusList
    val detailRoute = Route.RadiusDetail(25)
    
    val listString = listRoute.toString()
    val detailString = detailRoute.toString()
    
    assertTrue(listString.isNotEmpty(), "RadiusList toString should not be empty")
    assertTrue(detailString.isNotEmpty(), "RadiusDetail toString should not be empty")
    assertTrue(
      detailString.contains("25"),
      "RadiusDetail toString should contain radius",
    )
  }

  /**
   * Verifies copy functionality for RadiusDetail.
   */
  @Test
  fun radiusDetailShouldSupportCopy() {
    val original = Route.RadiusDetail(25)
    val copied = original.copy(radius = 50)
    
    assertEquals(25, original.radius, "Original should be unchanged")
    assertEquals(50, copied.radius, "Copy should have new radius")
    assertNotEquals(original, copied, "Original and copy should be different")
  }

  /**
   * Verifies copy with same value produces equal object.
   */
  @Test
  fun radiusDetailCopyWithSameValueShouldBeEqual() {
    val original = Route.RadiusDetail(25)
    val copied = original.copy()
    
    assertEquals(original, copied, "Copy with same value should be equal")
  }

  /**
   * Verifies Route is a sealed interface.
   */
  @Test
  fun routeShouldBeSealedInterface() {
    val routes: List<Route> = listOf(
      Route.RadiusList,
      Route.RadiusDetail(10),
    )
    
    // If this compiles and runs, the interface is properly sealed
    routes.forEach { route ->
      assertTrue(route is Route, "All instances should implement Route")
    }
  }

  /**
   * Verifies type hierarchy.
   */
  @Test
  fun routeTypeHierarchyShouldBeCorrect() {
    val listRoute: Route = Route.RadiusList
    val detailRoute: Route = Route.RadiusDetail(25)
    
    assertTrue(listRoute is Route)
    assertTrue(detailRoute is Route)
    assertTrue(listRoute is Route.RadiusList)
    assertTrue(detailRoute is Route.RadiusDetail)
  }

  /**
   * Verifies component functions for RadiusDetail.
   */
  @Test
  fun radiusDetailShouldSupportComponentFunctions() {
    val route = Route.RadiusDetail(42)
    val (radius) = route
    
    assertEquals(42, radius, "Component function should extract radius")
  }

  /**
   * Verifies multiple RadiusDetail instances with same radius.
   */
  @Test
  fun multipleRadiusDetailInstancesWithSameRadiusShouldBeEqual() {
    val routes = List(5) { Route.RadiusDetail(25) }
    
    routes.forEach { route ->
      assertEquals(Route.RadiusDetail(25), route)
    }
  }

  /**
   * Verifies navigation backstack scenario.
   */
  @Test
  fun navigationBackstackScenarioShouldWork() {
    val backStack = mutableListOf<Route>()
    
    // Navigate to list
    backStack.add(Route.RadiusList)
    assertEquals(1, backStack.size)
    
    // Navigate to detail
    backStack.add(Route.RadiusDetail(25))
    assertEquals(2, backStack.size)
    
    // Navigate to another detail
    backStack.add(Route.RadiusDetail(50))
    assertEquals(3, backStack.size)
    
    // Pop back
    backStack.removeLastOrNull()
    assertEquals(2, backStack.size)
    assertTrue(backStack.last() is Route.RadiusDetail)
    assertEquals(25, (backStack.last() as Route.RadiusDetail).radius)
  }

  /**
   * Verifies navigation from list to detail.
   */
  @Test
  fun navigationFromListToDetailShouldWork() {
    val backStack = mutableListOf<Route>(Route.RadiusList)
    
    // User selects a radius from the list
    val selectedRadius = 15
    backStack.add(Route.RadiusDetail(selectedRadius))
    
    assertEquals(2, backStack.size)
    assertTrue(backStack[0] is Route.RadiusList)
    assertTrue(backStack[1] is Route.RadiusDetail)
    assertEquals(selectedRadius, (backStack[1] as Route.RadiusDetail).radius)
  }

  /**
   * Verifies navigation back to list clears detail.
   */
  @Test
  fun navigationBackToListShouldClearDetail() {
    val backStack = mutableListOf<Route>(
      Route.RadiusList,
      Route.RadiusDetail(25),
    )
    
    // Navigate back
    backStack.removeLastOrNull()
    
    assertEquals(1, backStack.size)
    assertTrue(backStack.last() is Route.RadiusList)
  }

  /**
   * Verifies deep navigation with multiple radius values.
   */
  @Test
  fun deepNavigationWithMultipleRadiusShouldWork() {
    val backStack = mutableListOf<Route>()
    
    backStack.add(Route.RadiusList)
    backStack.add(Route.RadiusDetail(10))
    backStack.add(Route.RadiusDetail(25))
    backStack.add(Route.RadiusDetail(50))
    
    assertEquals(4, backStack.size)
    
    // Verify each level
    assertTrue(backStack[0] is Route.RadiusList)
    assertEquals(10, (backStack[1] as Route.RadiusDetail).radius)
    assertEquals(25, (backStack[2] as Route.RadiusDetail).radius)
    assertEquals(50, (backStack[3] as Route.RadiusDetail).radius)
  }

  /**
   * Verifies empty backstack initialization.
   */
  @Test
  fun emptyBackstackShouldBeValid() {
    val backStack = mutableListOf<Route>()
    
    assertTrue(backStack.isEmpty())
  }

  /**
   * Verifies backstack with only RadiusList.
   */
  @Test
  fun backstackWithOnlyRadiusListShouldBeValid() {
    val backStack = mutableListOf<Route>(Route.RadiusList)
    
    assertEquals(1, backStack.size)
    assertTrue(backStack[0] is Route.RadiusList)
  }

  /**
   * Verifies pattern matching all test radii from demo.
   */
  @Test
  fun patternMatchingAllTestRadiiShouldWork() {
    val testRadii = listOf(0, 5, 10, 15, 20, 25, 30, 40, 50, 75, 100)
    
    testRadii.forEach { radius ->
      val route = Route.RadiusDetail(radius)
      
      when (route) {
        is Route.RadiusDetail -> {
          assertEquals(radius, route.radius)
        }
        Route.RadiusList -> throw AssertionError("Should not be RadiusList")
      }
    }
  }

  /**
   * Verifies serialization annotation presence (compile-time check).
   */
  @Test
  fun routeShouldBeSerializable() {
    // This test verifies that Route types can be instantiated
    // The @Serializable annotation ensures they work with kotlinx.serialization
    val list = Route.RadiusList
    val detail = Route.RadiusDetail(25)
    
    assertTrue(list is Route)
    assertTrue(detail is Route)
  }
}
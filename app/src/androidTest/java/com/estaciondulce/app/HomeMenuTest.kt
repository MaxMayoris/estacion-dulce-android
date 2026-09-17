package com.estaciondulce.app

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.estaciondulce.app.activities.HomeActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeMenuTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(HomeActivity::class.java)

    @Test
    fun testClickProductsCard_opensProductsFragment() {
        // Wait for UI to settle
        Thread.sleep(1000)
        
        // Click on the Products card
        onView(withId(R.id.productsCard)).perform(click())
        
        // Wait for fragment transition
        Thread.sleep(1000)
        
        // Assert the search bar in ProductFragment is displayed
        onView(withId(R.id.searchBar)).check(matches(isDisplayed()))
    }

    @Test
    fun testClickMovementsCard_opensMovementsFragment() {
        Thread.sleep(1000)
        
        onView(withId(R.id.movementsCard)).perform(click())
        
        Thread.sleep(1000)
        
        // Assert the tabs container in MovementFragment is displayed
        onView(withId(R.id.tabsContainer)).check(matches(isDisplayed()))
    }

    @Test
    fun testClickRecipesCard_opensRecipesFragment() {
        Thread.sleep(1000)
        
        onView(withId(R.id.recipesCard)).perform(click())
        
        Thread.sleep(1000)
        
        // Assert the search bar in RecipeFragment is displayed
        onView(withId(R.id.searchBar)).check(matches(isDisplayed()))
    }
}

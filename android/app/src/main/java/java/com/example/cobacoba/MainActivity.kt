package com.example.cobacoba

import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.example.cobacoba.databinding.ActivityMainBinding
import com.google.android.material.navigation.NavigationView
import java.text.DateFormat
import java.util.Calendar

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var fragmentManager: FragmentManager
    private lateinit var binding: ActivityMainBinding
    private lateinit var toggle: ActionBarDrawerToggle
    private var isInSubmenuFragment = false
    private val TAG = "MainActivity"

    private val updateHandler = Handler()
    private lateinit var dateTextView: TextView
    private lateinit var timeTextView: TextView

    private val updateRunnable = object : Runnable {
        override fun run() {
            val calendar = Calendar.getInstance()
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            val dayName = when (dayOfWeek) {
                Calendar.SUNDAY -> "Sunday"
                Calendar.MONDAY -> "Monday"
                Calendar.TUESDAY -> "Tuesday"
                Calendar.WEDNESDAY -> "Wednesday"
                Calendar.THURSDAY -> "Thursday"
                Calendar.FRIDAY -> "Friday"
                Calendar.SATURDAY -> "Saturday"
                else -> "Unknown"
            }

            val month = calendar.get(Calendar.MONTH)
            val monthName = when (month) {
                Calendar.JANUARY -> "January"
                Calendar.FEBRUARY -> "February"
                Calendar.MARCH -> "March"
                Calendar.APRIL -> "April"
                Calendar.MAY -> "May"
                Calendar.JUNE -> "June"
                Calendar.JULY -> "July"
                Calendar.AUGUST -> "August"
                Calendar.SEPTEMBER -> "September"
                Calendar.OCTOBER -> "October"
                Calendar.NOVEMBER -> "November"
                Calendar.DECEMBER -> "December"
                else -> "Unknown"
            }

            val day = calendar.get(Calendar.DAY_OF_MONTH)
            val year = calendar.get(Calendar.YEAR)
            val formattedDate = "Today is $dayName, $monthName $day, $year"
            val formattedTime = DateFormat.getTimeInstance().format(calendar.time)

            dateTextView.text = formattedDate
            timeTextView.text = formattedTime

            updateHandler.postDelayed(this, 1000) // update every second
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDateTimeHeader()
        setupToolbarAndDrawer()
        setupBottomNavigation()

        fragmentManager = supportFragmentManager
        fragmentManager.addOnBackStackChangedListener {
            if (fragmentManager.backStackEntryCount == 0) {
                showBottomNav()
                enableDrawer()
                isInSubmenuFragment = false
            }
        }

        if (savedInstanceState == null) {
            openMainFragment(HomeFragment())
        }
    }

    private fun setupDateTimeHeader() {
        val headerView = binding.navView.getHeaderView(0)
        dateTextView = headerView.findViewById(R.id.xml_text_date)
        timeTextView = headerView.findViewById(R.id.xml_text_time)
        updateRunnable.run()
    }

    private fun setupToolbarAndDrawer() {
        setSupportActionBar(binding.toolbar)
        toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar,
            R.string.nav_open, R.string.nav_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        binding.navView.setNavigationItemSelectedListener(this)
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.bottom_home -> openMainFragment(HomeFragment())
                R.id.bottom_alerts -> openMainFragment(AlertsFragment())
                R.id.bottom_devices -> openMainFragment(DevicesFragment())
                else -> return@setOnItemSelectedListener false
            }
            true
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_settings -> openSubmenuFragment(SettingsFragment())
            R.id.nav_logs -> openSubmenuFragment(LogsFragment())
            R.id.nav_help -> openSubmenuFragment(HelpFragment())
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun openMainFragment(fragment: Fragment) {
        showBottomNav()
        enableDrawer()
        isInSubmenuFragment = false
        clearBackStack()
        val transaction = fragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, fragment)
        transaction.commit()
    }

    private fun openSubmenuFragment(fragment: Fragment) {
        hideBottomNav()
        disableDrawer()
        isInSubmenuFragment = true
        val transaction = fragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }

    private fun hideBottomNav() {
        binding.bottomNavigation.visibility = View.GONE
    }

    private fun showBottomNav() {
        binding.bottomNavigation.visibility = View.VISIBLE
    }

    private fun enableDrawer() {
        toggle.isDrawerIndicatorEnabled = true
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setHomeButtonEnabled(true)
        toggle.syncState()
        toggle.toolbarNavigationClickListener = null
    }

    private fun disableDrawer() {
        toggle.isDrawerIndicatorEnabled = false
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        toggle.toolbarNavigationClickListener = View.OnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun clearBackStack() {
        if (fragmentManager.backStackEntryCount > 0) {
            val first = fragmentManager.getBackStackEntryAt(0)
            fragmentManager.popBackStack(first.id, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
            if (fragmentManager.backStackEntryCount == 0 && !isInSubmenuFragment) {
                showBottomNav()
                enableDrawer()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        updateHandler.removeCallbacks(updateRunnable)
    }
}

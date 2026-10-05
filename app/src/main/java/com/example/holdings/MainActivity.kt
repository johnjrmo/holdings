package com.example.holdings

import android.app.Activity
import android.os.Bundle
import android.widget.EditText
import com.example.holdings.databinding.ActivityMainBinding
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var binding: ActivityMainBinding
    private val preferences by lazy { getSharedPreferences("holdings", MODE_PRIVATE) }
    private val executor = Executors.newSingleThreadExecutor()
    private var goldPrice = 0.0
    private var silverPrice = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.goldOunces.setText(preferences.getString("gold_ounces", ""))
        binding.silverOunces.setText(preferences.getString("silver_ounces", ""))
        binding.saveButton.setOnClickListener { saveHoldings() }
        binding.refreshButton.setOnClickListener { refreshPrices() }
        binding.calculateButton.setOnClickListener { calculateConversion() }
        updateTotal()
    }

    private fun saveHoldings() {
        preferences.edit()
            .putString("gold_ounces", binding.goldOunces.text.toString())
            .putString("silver_ounces", binding.silverOunces.text.toString())
            .apply()
        updateTotal()
        binding.errorText.text = "Holdings saved on this device."
    }

    private fun refreshPrices() {
        binding.refreshButton.isEnabled = false
        binding.errorText.text = "Refreshing market prices..."
        executor.execute {
            try {
                val latestGold = fetchPrice("GC=F")
                val latestSilver = fetchPrice("SI=F")
                runOnUiThread {
                    goldPrice = latestGold
                    silverPrice = latestSilver
                    binding.goldPrice.text = "Price: ${formatCurrency(goldPrice)} per oz"
                    binding.silverPrice.text = "Price: ${formatCurrency(silverPrice)} per oz"
                    binding.lastUpdated.text = "Updated just now"
                    binding.errorText.text = ""
                    binding.refreshButton.isEnabled = true
                    updateTotal()
                }
            } catch (exception: Exception) {
                runOnUiThread {
                    binding.errorText.text = "Could not refresh prices. Check your connection."
                    binding.refreshButton.isEnabled = true
                }
            }
        }
    }

    private fun calculateConversion() {
        val targetAmount = binding.targetAmount.text.toString().toDoubleOrNull()
        if (targetAmount == null || targetAmount <= 0.0) {
            binding.conversionResult.text = "Enter a target dollar amount."
            return
        }
        if (goldPrice <= 0.0 || silverPrice <= 0.0) {
            binding.conversionResult.text = "Refresh prices before calculating."
            return
        }

        val goldOunces = targetAmount * 0.68 / goldPrice * 1.05
        val silverOunces = targetAmount * 0.32 / silverPrice * 1.10
        binding.conversionResult.text = buildString {
            append("Gold: ")
            append(formatOunces(goldOunces))
            append(" oz (68% allocation, 5% conversion cost)\n")
            append("Silver: ")
            append(formatOunces(silverOunces))
            append(" oz (32% allocation, 10% conversion cost)")
        }
    }

    private fun fetchPrice(symbol: String): Double {
        val url = URL("https://query1.finance.yahoo.com/v8/finance/chart/$symbol?range=1d&interval=1m")
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("User-Agent", "Holdings Android app")
        return try {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(response)
                .getJSONObject("chart")
                .getJSONArray("result")
                .getJSONObject(0)
                .getJSONObject("meta")
                .getDouble("regularMarketPrice")
        } finally {
            connection.disconnect()
        }
    }

    private fun updateTotal() {
        val goldOunces = value(binding.goldOunces)
        val silverOunces = value(binding.silverOunces)
        val total = goldOunces * goldPrice + silverOunces * silverPrice
        binding.totalValue.text = formatCurrency(total)
    }

    private fun value(field: EditText): Double = field.text.toString().toDoubleOrNull() ?: 0.0

    private fun formatCurrency(value: Double): String = NumberFormat.getCurrencyInstance(Locale.US).format(value)

    private fun formatOunces(value: Double): String = String.format(Locale.US, "%.3f", value)

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}

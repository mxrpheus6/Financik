package com.mxrph.financik

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.mxrph.financik.databinding.ActivityAddTransactionBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.round

class AddTransactionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddTransactionBinding
    private lateinit var db: FirebaseFirestore
    private val calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddTransactionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()

        val types = arrayOf("Income", "Expense")
        binding.transactionTypeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types)

        loadCategories()

        binding.transactionTypeSpinner.setSelection(-1)
        binding.categorySpinner.setSelection(-1)

        binding.dateEditText.setOnClickListener {
            showDatePickerDialog()
        }

        binding.saveTransactionButton.setOnClickListener {
            saveTransaction()
        }
    }

    private fun loadCategories() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            db.collection("users").document(userId).collection("categories").get()
                .addOnSuccessListener { result ->
                    val categories = result.mapNotNull { it.getString("name") }.distinct()
                    binding.categorySpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
                    binding.categorySpinner.setSelection(-1)
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error loading categories", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(this, "User not authorized", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDatePickerDialog() {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            calendar.set(selectedYear, selectedMonth, selectedDay)
            updateDateInView()
        }, year, month, day)

        datePickerDialog.show()
    }

    private fun updateDateInView() {
        val format = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        binding.dateEditText.setText(format.format(calendar.time))
    }

    private fun saveTransaction() {
        val type = binding.transactionTypeSpinner.selectedItem.toString()
        val amount = binding.amountEditText.text.toString().toDoubleOrNull()
        val category = binding.categorySpinner.selectedItem.toString()
        val title = binding.titleEditText.text.toString()
        val date = binding.dateEditText.text.toString()

        if (amount != null) {
            val userId = FirebaseAuth.getInstance().currentUser?.uid
            if (userId != null) {
                val transaction = hashMapOf(
                    "type" to type,
                    "amount" to amount,
                    "category" to category,
                    "title" to title,
                    "date" to date,
                    "timestamp" to FieldValue.serverTimestamp() // Добавляем поле timestamp
                )

                db.collection("users").document(userId).collection("transactions").add(transaction)
                    .addOnSuccessListener {
                        updateBalance(userId, amount, type)
                        Toast.makeText(this, "Transaction saved", Toast.LENGTH_SHORT).show()

                        val intent = Intent(this, MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Error saving", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(this, "User not authorized", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Enter correct amount", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateBalance(userId: String, amount: Double, type: String) {
        val userDocRef = db.collection("users").document(userId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(userDocRef)
            val currentBalance = snapshot.getDouble("balance") ?: 0.0

            var newBalance = if (type == "Income") {
                currentBalance + amount
            } else {
                currentBalance - amount
            }
            newBalance = round(newBalance * 100) / 100

            transaction.update(userDocRef, "balance", newBalance)
            newBalance
        }.addOnSuccessListener { newBalance ->
            val preferences = getSharedPreferences("financik_prefs", MODE_PRIVATE)
            preferences.edit().putFloat("balance", newBalance.toFloat()).apply()

            Toast.makeText(this, "Balance updated", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            Toast.makeText(this, "Error updating balance", Toast.LENGTH_SHORT).show()
        }
    }

}

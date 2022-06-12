package tech.dzolotov.counterappmvi

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

interface MainView {
    fun render(state: State)
}

@AndroidEntryPoint
class CounterActivity : MainView, AppCompatActivity() {

    @Inject
    lateinit var factory: ViewModelProvider.Factory

    val viewModel by viewModels<MainViewModel>(factoryProducer = { factory })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        viewModel.bindView(this)
        viewModel.bindEffects(this, applicationContext)
        findViewById<Button>(R.id.increase_button).setOnClickListener {
            viewModel.increment()
        }
    }

    override fun render(state: State) {
        //обновление view по новому состоянию
        findViewById<TextView>(R.id.counter).text =
            if (state.counter != null) "Counter: ${state.counter}" else "Click below for increment"
        val description = findViewById<TextView>(R.id.description)
        if (state.message != null) {
            description.text = when (state.message) {
                is DescriptionResult.Loading -> "Loading"
                is DescriptionResult.Error -> "Error"
                is DescriptionResult.Success -> state.message.text
            }
        } else {
            findViewById<TextView>(R.id.description).text = ""
        }
    }
}
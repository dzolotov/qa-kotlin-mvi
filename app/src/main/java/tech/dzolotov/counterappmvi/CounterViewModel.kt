package tech.dzolotov.counterappmvi

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.collections.set

sealed class DescriptionResult {
    data class Success(val text: String) : DescriptionResult()
    object Error : DescriptionResult()
    object Loading : DescriptionResult()
}

fun ViewModel.overrideScope(scope: CoroutineScope) {
    val tags = ViewModel::class.java.getDeclaredField("mBagOfTags")
    tags.isAccessible = true
    val tagsValue = tags.get(this) as HashMap<String, Any>
    tagsValue["androidx.lifecycle.ViewModelCoroutineScope.JOB_KEY"] = scope as Any
}

@HiltViewModel
class MainViewModel @Inject constructor(val store: IStore) : ViewModel() {

    init {
        store.subscribe()
        loadingData()
    }

    fun increment() {
        viewModelScope.launch {
            store.dispatch(Intent.Increment)
        }
    }

    private fun loadingData() {
        viewModelScope.launch {
            store.dispatch(Intent.Load)
        }
    }

    fun bindView(view: MainView) {
        viewModelScope.launch {
            store.stateFlow.collect {
                view.render(it)
                store.idlingResource.decrement()
            }
        }
    }

    fun bindEffects(lifecycleOwner: LifecycleOwner, context: Context) {
        store.sideEffectsFlow.observeOnLifecycle(lifecycleOwner) {
            when (it) {
                is SideEffect.ShowToast -> Toast.makeText(
                    context,
                    "Counter = 3",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }
}

class ViewModelFactory @Inject constructor() : ViewModelProvider.Factory {
    val cached = mutableMapOf<String, ViewModel?>()

    @Inject
    lateinit var store: IStore

    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        val key = modelClass.canonicalName
        if (!cached.containsKey(key)) {
            cached[key] = MainViewModel(store)
        }
        return cached[key] as T
    }
}
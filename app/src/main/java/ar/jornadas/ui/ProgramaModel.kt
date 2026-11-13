package ar.jornadas.ui

import android.app.Application
import androidx.lifecycle.*
import ar.jornadas.Servicios
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ProgramaModel(app: Application, private val estado: SavedStateHandle) : AndroidViewModel(app) {
    private val servicios = Servicios(app)
    val mesa = servicios.mesa
    private val repo = servicios.agenda
    val charlas = repo.programa.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val hayEdicion = repo.hayEdicion.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val dia = estado.getStateFlow("dia", 0)
    val tema = estado.getStateFlow("tema", "Todos")
    val aviso = MutableStateFlow("")
    val cargando = MutableStateFlow(false)

    fun elegirDia(d: Int) { estado["dia"] = d }
    fun elegirTema(t: String) { estado["tema"] = t }

    fun recargar() {
        viewModelScope.launch {
            cargando.value = true
            aviso.value = ""
            try {
                repo.sincronizar(mesa.revision)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aviso.value = "No se pudo consultar el programa"
            } finally {
                cargando.value = false
            }
        }
    }

    fun marcar(id: String, favorita: Boolean) {
        viewModelScope.launch { repo.cambiarFavorita(id, favorita, mesa.revision) }
    }
}

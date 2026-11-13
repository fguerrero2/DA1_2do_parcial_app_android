package ar.jornadas.data

class AgendaRepository(private val api: ProgramaApi, private val dao: CarteleraDao) {
    val programa = dao.observar()
    val hayEdicion = dao.observarHayEdicion()

    suspend fun sincronizar(version: Int) {
        val fichas = api.consultar().map {
            Charla(it.id, it.title, it.track, it.day, it.time, it.room, it.speaker, it.summary)
        }
        dao.aplicarInstantanea(fichas, version)
    }

    suspend fun cambiarFavorita(id: String, favorita: Boolean, version: Int) =
        dao.fijarFavoritaEnVersion(id, favorita, version)
}

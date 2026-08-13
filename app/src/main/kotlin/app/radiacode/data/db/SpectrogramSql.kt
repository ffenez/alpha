package app.radiacode.data.db

/**
 * Запросы истории спектрограммы вынесены в константы (тот же приём, что у
 * [TrackGridSql]): JVM-тест на sqlite-jdbc гоняет ИМЕННО тот SQL, который
 * стоит в аннотации DAO, а не его пересказ.
 */
object SpectrogramSql {

    /**
     * Срезы, ПЕРЕСЕКАЮЩИЕ окно (не только начавшиеся в нём: срез, начатый до
     * `from`, — часть картинки), новейшие первыми и не больше `:limit` строк.
     * Порядок именно такой, чтобы крышка строк срезала ДАЛЬНИЙ край окна:
     * картинку смотрят от «сейчас» назад.
     */
    const val WINDOW = """
        SELECT * FROM spectrogram_slices
        WHERE endMillis >= :from AND startMillis <= :to
        ORDER BY startMillis DESC LIMIT :limit
    """

    /** Срезы диапазона по возрастанию — вход прореживания. */
    const val RANGE = """
        SELECT * FROM spectrogram_slices
        WHERE startMillis >= :from AND startMillis < :to
        ORDER BY startMillis LIMIT :limit
    """
}

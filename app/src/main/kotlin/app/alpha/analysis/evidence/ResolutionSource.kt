package app.alpha.analysis.evidence

/**
 * Действующая модель разрешения приложения: измеренная, если человек её
 * принял, иначе никакая (и тогда работает прежнее √E-приближение).
 *
 * ## Почему это общий держатель, а не параметр
 *
 * Правильнее было бы протащить модель параметром через каждую точку вызова.
 * Но ширину линии спрашивают поиск пиков, допуск совпадения, ROI радона и
 * экраны — то есть половина приложения, включая композиции, которые эта
 * задача не трогает. Держатель с ОДНИМ писателем (`AppGraph` подписан на
 * настройку) даёт тот же результат без правки чужих экранов; читатели
 * остаются чистыми функциями от того, что в нём лежит.
 *
 * ## Серийник обязателен к совпадению
 *
 * Коэффициенты измерены на конкретном кристалле. Пока подключён прибор с
 * ДРУГИМ серийником, модель не действует — молча применять чужую ширину
 * хуже, чем работать по приближению. Прибор не подключён — модель
 * действует: приложение открывают и без связи, а история спектров осталась
 * от того же прибора.
 */
object ResolutionSource {

    @Volatile
    private var accepted: AcceptedResolution? = null

    @Volatile
    private var connectedSerial: String? = null

    /** Что принято и хранится — независимо от того, действует ли оно сейчас. */
    val stored: AcceptedResolution? get() = accepted

    /** Модель, действующая ПРЯМО СЕЙЧАС; null — работает приближение. */
    val active: AcceptedResolution?
        get() {
            val record = accepted ?: return null
            val serial = connectedSerial ?: return record
            val recorded = record.deviceSerial ?: return record
            return if (serial == recorded) record else null
        }

    /** Единственный писатель — `AppGraph`. `null` стирает принятую модель. */
    fun install(record: AcceptedResolution?) {
        accepted = record
    }

    /** Серийник подключённого прибора; `null` — прибора нет. */
    fun onDevice(serial: String?) {
        connectedSerial = serial
    }

    /** FWHM по действующей модели; null — модели нет, спрашивайте приближение. */
    fun fwhmKeV(energyKeV: Double): Double? = active?.model()?.fwhmKeV(energyKeV)

    /** Действующая модель или [fallback], если измеренной нет. */
    fun modelOr(fallback: ResolutionModel): ResolutionModel =
        active?.model() ?: fallback
}

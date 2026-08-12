package app.radiacode.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Управление самим прибором: то, что он делает БЕЗ телефона.
 *
 * Отдельный хаб, а не строчка в настройках приложения, ровно по одной
 * причине: звук приложения и звук прибора — разные вещи, и человек должен
 * понимать, будет ли прибор пищать, когда телефон в кармане или выключен.
 * Смешать их в один тумблер значило бы соврать про поведение железа.
 *
 * ## Чего здесь нет и почему
 *
 * Только два переключателя — звук и вибрация (`VSFR SOUND_ON`, `VIBRO_ON`,
 * та же пара, что в референсной реализации `cdump/radiacode`). Пороги
 * прибора, яркость, ориентация экрана и время не трогаются: их регистры мы не
 * проверяли на живом приборе, а неверная запись меняет поведение чужого
 * устройства необратимо для владельца.
 *
 * ## Честность состояния
 *
 * Приложение НЕ читает эти регистры обратно: команда записи подтверждается
 * прибором, но опросить текущее состояние тем же документированным путём мы
 * не умеем. Поэтому [applied] хранит только то, что мы САМИ отправили в этом
 * сеансе, а до первой команды состояние неизвестно — и UI обязан говорить
 * «неизвестно», а не показывать выключенный тумблер как факт.
 */
class DeviceControlHub {

    /** Что можно попросить у прибора. */
    sealed interface Command {
        data class Sound(val on: Boolean) : Command
        data class Vibro(val on: Boolean) : Command
    }

    /**
     * Отправленные в этом сеансе значения; null — мы прибору ничего не
     * говорили и его состояния не знаем.
     */
    data class Applied(val sound: Boolean? = null, val vibro: Boolean? = null)

    private val _applied = MutableStateFlow(Applied())
    val applied: StateFlow<Applied> = _applied.asStateFlow()

    private val _commands = MutableSharedFlow<Command>(extraBufferCapacity = 8)
    val commands: SharedFlow<Command> = _commands.asSharedFlow()

    fun request(command: Command) {
        _commands.tryEmit(command)
    }

    /** Вызывается сервисом ПОСЛЕ того, как прибор подтвердил запись. */
    internal fun onApplied(command: Command) {
        _applied.value = when (command) {
            is Command.Sound -> _applied.value.copy(sound = command.on)
            is Command.Vibro -> _applied.value.copy(vibro = command.on)
        }
    }

    /** Связь потеряна: что стоит в приборе сейчас, мы снова не знаем. */
    internal fun onDisconnected() {
        _applied.value = Applied()
    }
}

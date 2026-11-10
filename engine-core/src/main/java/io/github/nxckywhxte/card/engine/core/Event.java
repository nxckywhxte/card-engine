package io.github.nxckywhxte.card.engine.core;

import java.time.Instant;

/**
 * Неизменяемый факт произошедшего изменения в игре.
 *
 * <p>Это одна из семи базовых абстракций ядра {@code engine-core} и центральная концепция паттерна
 * <b>Event Sourcing</b>, используемого в модуле {@code engine-persistence}.
 *
 * <h3>Философия событий:</h3>
 *
 * <p>В отличие от {@link Action} (намерение), {@code Event} — это <b>свершившийся факт</b>.
 * Действие может быть отклонено (невалидно), но событие — никогда. Если событие создано, оно
 * становится частью неизменяемой истории игры.
 *
 * <h3>Контракты и инварианты:</h3>
 *
 * <ul>
 *   <li><b>Неизменяемость (Immutability):</b> после создания событие не может быть изменено. Все
 *       поля должны быть {@code final} (в record — автоматически).
 *   <li><b>Уникальность ID:</b> каждое событие имеет уникальный идентификатор в рамках сессии.
 *   <li><b>Монотонность sequenceNumber:</b> порядковый номер события должен строго возрастать (1,
 *       2, 3...). Это критично для:
 *       <ul>
 *         <li>Восстановления порядка событий при replay.
 *         <li>Синхронизации клиентов ({@code engine-network}).
 *         <li>Привязки снапшотов к конкретной точке в истории ({@code engine-persistence}).
 *       </ul>
 *   <li><b>Детерминированность timestamp:</b> время создания фиксируется в момент генерации события
 *       и не меняется.
 * </ul>
 *
 * <h3>Связь с Action:</h3>
 *
 * <p>Каждое {@link Action} при успешном выполнении ({@code Action.execute()}) порождает ровно одно
 * {@code Event}. Событие содержит:
 *
 * <ul>
 *   <li>ID действия, которое его породило (для трассировки).
 *   <li>Данные об изменении (какие сущности перемещены, какие атрибуты изменены).
 *   <li>Временную метку и порядковый номер.
 * </ul>
 *
 * <h3>Примеры событий:</h3>
 *
 * <pre>{@code
 * // Пример 1: Карта сыграна
 * public record CardPlayedEvent(
 *     String id,
 *     String actionId,
 *     Player player,
 *     String cardId,
 *     String fromZone,
 *     String toZone,
 *     Instant timestamp,
 *     long sequenceNumber
 * ) implements Event {}
 *
 * // Пример 2: Игрок выбыл
 * public record PlayerEliminatedEvent(
 *     String id,
 *     String actionId,
 *     Player player,
 *     String reason,
 *     Instant timestamp,
 *     long sequenceNumber
 * ) implements Event {}
 *
 * // Пример 3: Состояние изменилось
 * public record StateTransitionedEvent(
 *     String id,
 *     String actionId,
 *     String fromState,
 *     String toState,
 *     Instant timestamp,
 *     long sequenceNumber
 * ) implements Event {}
 * }</pre>
 *
 * <h3>Использование в Event Sourcing:</h3>
 *
 * <p>Поток событий ({@code Event Stream}) — это единственная истина о состоянии игры. Чтобы
 * восстановить текущее состояние, модуль {@code engine-persistence} "проигрывает" все события по
 * порядку на пустом {@link GameContext}.
 *
 * <h3>Использование в State Projections:</h3>
 *
 * <p>Модуль {@code engine-network} подписывается на события и после каждого нового события
 * генерирует обновлённые проекции состояния для клиентов.
 *
 * @author nxckywhxte
 * @since 0.1.0
 * @see Action#execute(GameContext)
 * @see GameContext
 * @see Instant
 */
public interface Event {
  /**
   * Возвращает уникальный идентификатор события в рамках игровой сессии.
   *
   * <p>ID используется для:
   *
   * <ul>
   *   <li>Идентификации события в {@code Event Store} ({@code engine-persistence}).
   *   <li>Трассировки: по ID события можно найти породившее его {@link Action}.
   *   <li>Защиты от дублирования в {@code engine-network}.
   * </ul>
   *
   * <p><b>Контракт:</b> значение не может быть {@code null} или пустой строкой. Рекомендуется
   * использовать {@code UUID.randomUUID().toString()}.
   *
   * @return уникальный идентификатор события
   */
  String getEventId();

  /**
   * Возвращает время создания события.
   *
   * <p>Используется для:
   *
   * <ul>
   *   <li>Хронологического упорядочивания событий.
   *   <li>Системы реплеев ({@code ReplayPlayer}) — для анимации с правильной скоростью
   *       воспроизведения.
   *   <li>Отладки и логирования.
   * </ul>
   *
   * <p><b>Контракт:</b> значение не может быть {@code null}. Время фиксируется в момент создания
   * события и не меняется.
   *
   * @return время создания события (не {@code null})
   * @see Instant
   */
  Instant getTimestamp();

  /**
   * Возвращает порядковый номер события в потоке событий игры.
   *
   * <p>Это <b>критически важное</b> поле для Event Sourcing. Номер должен монотонно возрастать (1,
   * 2, 3...) в рамках одной игровой сессии.
   *
   * <p><b>Использование:</b>
   *
   * <ul>
   *   <li><b>Восстановление состояния:</b> {@code engine-persistence} загружает события начиная с
   *       {@code sequenceNumber} последнего снапшота.
   *   <li><b>Синхронизация клиентов:</b> {@code engine-network} использует номер для определения,
   *       какие события клиент ещё не получил ({@code lastReceivedSequenceNumber}).
   *   <li><b>Снапшоты:</b> каждый снапшот привязан к конкретному {@code sequenceNumber} — точке в
   *       истории игры.
   * </ul>
   *
   * <p><b>Контракт:</b> значение должно быть {@code >= 1}. Первое событие игры имеет номер 1.
   * Номера не должны иметь разрывов (1, 2, 3, а не 1, 3, 5).
   *
   * @return порядковый номер события (монотонно возрастающий)
   */
  long getSequenceNumber();
}

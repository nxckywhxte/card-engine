package io.github.nxckywhxte.card.engine.core;

/**
 * Представляет намерение игрока изменить состояние игры.
 *
 * <p>Это одна из семи базовых абстракций ядра {@code engine-core}. Действие — это "команда",
 * которую игрок хочет выполнить: сыграть карту, сделать ставку, взять карту из колоды, сказать
 * "пас" и т.д.
 *
 * <h3>Жизненный цикл действия:</h3>
 *
 * <ol>
 *   <li>Игрок инициирует действие (через UI или API).
 *   <li>Движок вызывает {@link #isValid(GameContext)} — проверка правил без побочных эффектов.
 *   <li>Если валидация пройдена, движок вызывает {@link #execute(GameContext)} — применение
 *       действия и генерация {@link Event}.
 *   <li>Событие сохраняется в {@code engine-persistence} и рассылается клиентам через {@code
 *       engine-network}.
 * </ol>
 *
 * <h3>Контракты и инварианты:</h3>
 *
 * <ul>
 *   <li><b>Разделение валидации и выполнения:</b> метод {@link #isValid(GameContext)} должен быть
 *       <b>чистой функцией</b> (без побочных эффектов). Он не должен изменять состояние игры. Все
 *       изменения происходят только в {@link #execute(GameContext)}.
 *   <li><b>Порядок вызовов:</b> {@code execute()} никогда не должен вызываться, если {@code
 *       isValid()} вернул {@code false}. Это гарантируется модулем {@code engine-fsm}.
 *   <li><b>Обязательный результат:</b> метод {@code execute()} обязан вернуть {@link Event},
 *       описывающий произошедшее изменение. Действие без события — нарушение контракта Event
 *       Sourcing.
 *   <li><b>Идентификация:</b> каждое действие должно иметь уникальный ID в рамках сессии (для
 *       логирования, undo и replay).
 * </ul>
 *
 * <h3>Двухуровневая валидация:</h3>
 *
 * <p>Валидация действия происходит на двух уровнях:
 *
 * <ol>
 *   <li><b>Уровень FSM</b> ({@code engine-fsm}): "Доступно ли это действие в текущем состоянии?"
 *       Например, {@code DefendAction} недоступно в состоянии {@code AttackState}. Эта проверка
 *       выполняется до вызова {@link #isValid(GameContext)}.
 *   <li><b>Уровень правил</b> (реализация {@link #isValid(GameContext)}): "Разрешено ли это
 *       действие по правилам игры?" Например, "можно ли побить этой картой атакующую?".
 * </ol>
 *
 * <h3>Примеры реализации:</h3>
 *
 * <pre>{@code
 * // Пример: действие "Сыграть карту" в Дураке
 * public record PlayCardAction(
 *     String id,
 *     Player player,
 *     String cardId,
 *     String targetZoneName
 * ) implements Action {
 *
 *     @Override
 *     public boolean isValid(GameContext context) {
 *         Zone<Card> hand = context.getZone("hand_" + player.getId());
 *         return hand.getAll().stream()
 *             .anyMatch(c -> c.getId().equals(cardId));
 *     }
 *
 *     @Override
 *     public Event execute(GameContext context) {
 *         Zone<Card> hand = context.getZone("hand_" + player.getId());
 *         Zone<Card> table = context.getZone("table");
 *         Card card = hand.getAll().stream()
 *             .filter(c -> c.getId().equals(cardId))
 *             .findFirst()
 *             .orElseThrow();
 *         hand.remove(card);
 *         table.add(card);
 *         return new CardPlayedEvent(id, player, card, Instant.now());
 *     }
 * }
 * }</pre>
 *
 * <h3>Связь с другими модулями:</h3>
 *
 * <ul>
 *   <li>{@code engine-fsm}: вызывает {@code isValid()} и {@code execute()}, генерирует {@link
 *       Event}.
 *   <li>{@code engine-network}: получает {@link Event} и рассылает клиентам через State
 *       Projections.
 *   <li>{@code engine-persistence}: сохраняет {@link Event} в Event Store.
 * </ul>
 *
 * @author nxckywhxte
 * @since 0.1.0
 * @see Event
 * @see State
 * @see GameContext
 * @see Player
 */
public interface Action {
  /**
   * Возвращает уникальный идентификатор действия в рамках игровой сессии.
   *
   * <p>ID используется для:
   *
   * <ul>
   *   <li>Логирования в {@code Event Sourcing}.
   *   <li>Механизма Undo (отмены последнего действия).
   *   <li>Защиты от дублирования сообщений в {@code engine-network} (idempotency key).
   *   <li>Связывания действия с порождённым им {@link Event}.
   * </ul>
   *
   * <p><b>Контракт:</b> значение не может быть {@code null} или пустой строкой. Уникальность
   * гарантируется в рамках одной {@code GameSession}. Рекомендуется использовать {@code
   * UUID.randomUUID().toString()}.
   *
   * @return уникальный идентификатор действия
   */
  String getActionId();

  /**
   * Возвращает игрока, инициировавшего действие.
   *
   * <p>Используется для:
   *
   * <ul>
   *   <li>Проверки, чей сейчас ход ({@code GameContext.getCurrentPlayer()}).
   *   <li>Определения владельца зон, из которых или в которые перемещаются сущности.
   *   <li>Логирования "кто что сделал".
   * </ul>
   *
   * <p><b>Контракт:</b> значение не может быть {@code null}.
   *
   * @return игрок-автор действия (не {@code null})
   * @see Player
   */
  Player getPlayer();

  /**
   * Проверяет, можно ли выполнить это действие в текущем состоянии игры.
   *
   * <p>Это <b>чистая функция</b>: она не должна изменять состояние игры. Все проверки должны быть
   * read-only.
   *
   * <p>Здесь реализуется валидация на уровне <b>правил игры</b>: "можно ли побить этой картой?",
   * "достаточно ли фишек для ставки?", "не превышено ли количество карт на столе?".
   *
   * <p><b>Важно:</b> проверка доступности действия в текущем состоянии ({@code
   * State.getAvailableActions()}) выполняется модулем {@code engine-fsm} <b>до</b> вызова этого
   * метода.
   *
   * <p><b>Контракт:</b>
   *
   * <ul>
   *   <li>Метод не должен изменять {@code context}.
   *   <li>Метод не должен иметь побочных эффектов (сеть, БД, консоль).
   *   <li>Метод должен быть детерминированным: при одинаковых входных данных всегда возвращать
   *       одинаковый результат.
   * </ul>
   *
   * @param context текущее состояние игры (не {@code null})
   * @return {@code true}, если действие можно выполнить; {@code false} иначе
   * @see #execute(GameContext)
   */
  boolean isValid(GameContext context);

  /**
   * Выполняет действие и возвращает событие, описывающее произошедшее изменение.
   *
   * <p>Этот метод <b>изменяет состояние игры</b>: перемещает сущности между зонами, обновляет
   * атрибуты контекста, меняет статусы игроков.
   *
   * <p><b>Контракт:</b>
   *
   * <ul>
   *   <li>Метод должен быть вызван <b>только</b> после успешной {@link #isValid(GameContext)}.
   *   <li>Метод обязан вернуть {@link Event} — неизменяемый факт произошедшего.
   *   <li>Все изменения состояния должны быть <b>атомарными</b>: либо всё выполнилось, либо ничего
   *       (в случае исключения состояние должно быть откачено).
   *   <li>Метод не должен генерировать несколько событий — только одно. Если действие сложное, оно
   *       должно быть разбито на несколько {@code Action}.
   * </ul>
   *
   * <p><b>Пример:</b>
   *
   * <pre>{@code
   * @Override
   * public Event execute(GameContext context) {
   *     Zone<Card> hand = context.getZone("hand_" + player.getId());
   *     Zone<Card> table = context.getZone("table");
   *     Card card = findCardInHand(hand);
   *     hand.remove(card);
   *     table.add(card);
   *     return new CardPlayedEvent(getId(), player, card, Instant.now());
   * }
   * }</pre>
   *
   * @param context текущее состояние игры (не {@code null})
   * @return событие, описывающее результат действия (не {@code null})
   * @throws IllegalStateException если действие невалидно (нарушение контракта)
   * @see #isValid(GameContext)
   * @see Event
   */
  Event execute(GameContext context);
}

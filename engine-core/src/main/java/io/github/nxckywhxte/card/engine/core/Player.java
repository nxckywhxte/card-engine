package io.github.nxckywhxte.card.engine.core;

/**
 * Представляет участника карточной игры.
 *
 * <p>Это одна из семи базовых абстракций ядра {@code engine-core}. Интерфейс намеренно
 * минималистичен и не содержит игровой логики — только идентификацию и статус участника.
 *
 * <h3>Контракты и инварианты:</h3>
 *
 * <ul>
 *   <li>{@link #getPlayerId()} должен возвращать <b>уникальный</b> идентификатор в рамках одной
 *       игровой сессии. Дублирование ID недопустимо и может привести к неопределённому поведению в
 *       модулях {@code engine-fsm} и {@code engine-network}.
 *   <li>Идентификатор должен быть <b>неизменяемым</b> на протяжении всей жизни игрока в сессии.
 *   <li>Статус {@link #getPlayerStatus()} может меняться в процессе игры под управлением модуля
 *       {@code engine-network} (при дисконнектах) и {@code engine-fsm} (при выбывании по правилам).
 * </ul>
 *
 * <h3>Связь с другими модулями:</h3>
 *
 * <ul>
 *   <li>{@code engine-fsm}: использует {@code Player} для определения текущего ходящего ({@code
 *       currentPlayer}) и валидации действий ({@code Action.getPlayer()}).
 *   <li>{@code engine-network}: привязывает {@code Player} к сетевому соединению (Netty {@code
 *       Channel}) для маршрутизации сообщений и State Projections.
 *   <li>{@code engine-core}: {@code Player} может быть владельцем {@link Zone} (например, руки
 *       игрока) через {@link Zone#getOwner()}.
 * </ul>
 *
 * <h3>Примеры реализации:</h3>
 *
 * <pre>{@code
 * // Простая реализация через record (Java 25)
 * public record SimplePlayer(String id, String name, PlayerStatus status) implements Player {
 *     public SimplePlayer {
 *         Objects.requireNonNull(id, "Player ID cannot be null");
 *         if (id.isBlank()) {
 *             throw new IllegalArgumentException("Player ID cannot be blank");
 *         }
 *     }
 * }
 *
 * // Использование
 * Player player = new SimplePlayer("player-1", "Alice", PlayerStatus.ACTIVE);
 * }</pre>
 *
 * @author nxckywhxte
 * @since 0.1.0
 * @see PlayerStatus
 * @see Zone#getOwner()
 * @see io.github.nxckywhxte.card.engine.core.Action#getPlayer()
 */
public interface Player {
  /**
   * Возвращает уникальный идентификатор игрока в рамках игровой сессии.
   *
   * <p>Идентификатор используется для:
   *
   * <ul>
   *   <li>Маршрутизации сетевых сообщений ({@code engine-network}).
   *   <li>Определения владельца {@link Zone}.
   *   <li>Логирования действий в {@code Event Sourcing} ({@code engine-persistence}).
   * </ul>
   *
   * <p><b>Контракт:</b> значение не может быть {@code null} или пустой строкой. Уникальность
   * гарантируется в рамках одной {@code GameSession}.
   *
   * @return уникальный идентификатор игрока (например, UUID или пользовательский ID)
   * @see Zone#getOwner()
   */
  String getPlayerId();

  /**
   * Возвращает отображаемое имя игрока.
   *
   * <p>Используется преимущественно в пользовательских интерфейсах и логах. Не должен
   * использоваться как уникальный идентификатор — для этого есть метод {@link #getPlayerId()}.
   *
   * <p><b>Контракт:</b> значение может быть {@code null}, если имя не задано, но стандартные
   * реализации обычно возвращают имя по умолчанию (например, {@code "Player-1"}).
   *
   * @return отображаемое имя игрока или {@code null}
   */
  String getPlayerName();

  /**
   * Возвращает текущий статус игрока.
   *
   * <p>Статус определяет, может ли игрок совершать действия и участвовать в игре. Подробнее о
   * семантике каждого статуса см. {@link PlayerStatus}.
   *
   * <p><b>Контракт:</b> метод не должен возвращать {@code null}. Значение по умолчанию для нового
   * игрока — {@link PlayerStatus#ACTIVE}.
   *
   * @return текущий статус игрока (не {@code null})
   * @see PlayerStatus
   */
  PlayerStatus getPlayerStatus();
}

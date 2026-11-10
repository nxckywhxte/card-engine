package io.github.nxckywhxte.card.engine.core;

/**
 * Текущий статус участника игры {@link Player}.
 *
 * <p>Статус используется модулем {@code engine-fsm} для принятия решений:
 *
 * <ul>
 *   <li>Может ли игрок совершать действия ({@link io.github.nxckywhxte.card.engine.core.Action}).
 *   <li>Должен ли ход быть передан следующему игроку.
 *   <li>Считается ли игрок выбывшим из партии.
 * </ul>
 *
 * <p>Переходы между статусами управляются модулем {@code engine-network} (при дисконнектах) и
 * модулем {@code engine-fsm} (при elimination по правилам игры).
 *
 * @author nxckywhxte
 * @since 0.1.0
 * @see Player#getPlayerStatus()
 */
public enum PlayerStatus {
  /**
   * Игрок активен, подключён к сети и может совершать действия.
   *
   * <p>Это статус по умолчанию для всех участников в начале игры. Только игроки со статусом {@code
   * ACTIVE} могут быть текущим ходящим ({@code currentPlayer}) в {@link
   * io.github.nxckywhxte.card.engine.core.GameContext}.
   */
  ACTIVE,
  /**
   * Игрок временно отключился от сети, но ещё не считается выбывшим.
   *
   * <p>Статус устанавливается модулем {@code engine-network} при обнаружении разрыва соединения
   * ({@code channelInactive} в Netty). Запускается таймер реконнекта (настраивается в {@code
   * GameConfig}).
   *
   * <p>Поведение во время {@code DISCONNECTED} определяется правилами игры:
   *
   * <ul>
   *   <li>В некоторых играх ход пропускается.
   *   <li>В других — включается бот-заглушка.
   *   <li>В третьих — игра ставится на паузу.
   * </ul>
   *
   * <p>Если игрок не вернётся до истечения {@code reconnectTimeout}, статус автоматически меняется
   * на {@link #ELIMINATED}.
   *
   * @see io.github.nxckywhxte.card.engine.network.GameSession#handleDisconnect(Player)
   */
  DISCONNECTED,
  /**
   * Игрок окончательно выбыл из игры.
   *
   * <p>Причины перехода в этот статус:
   *
   * <ul>
   *   <li>Проигрыш по правилам игры (например, остался с картами в дураке).
   *   <li>Сдача (fold в покере).
   *   <li>Истечение таймера реконнекта после {@link #DISCONNECTED}.
   *   <li>Явный выход из игры по команде игрока.
   * </ul>
   *
   * <p>Игрок со статусом {@code ELIMINATED}:
   *
   * <ul>
   *   <li>Не может совершать действия.
   *   <li>Пропускается при передаче хода ({@code TurnManager.nextPlayer()}).
   *   <li>Может стать наблюдателем ({@code Spectator}), если это разрешено правилами.
   * </ul>
   */
  ELIMINATED
}

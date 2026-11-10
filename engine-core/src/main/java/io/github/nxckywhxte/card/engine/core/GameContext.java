package io.github.nxckywhxte.card.engine.core;

import java.util.List;
import java.util.Optional;

/**
 * Снимок текущего состояния игры.
 *
 * <p>Это одна из семи базовых абстракций ядра {@code engine-core} и центральная точка доступа ко
 * всей информации об игре. {@code GameContext} объединяет игроков, зоны, атрибуты и текущее
 * состояние в единый объект, который передаётся между всеми компонентами движка.
 *
 * <h3>Роль в архитектуре:</h3>
 *
 * <p>{@code GameContext} является "контекстом выполнения" для:
 *
 * <ul>
 *   <li>{@link Action#isValid(GameContext)} и {@link Action#execute(GameContext)} — действия читают
 *       и изменяют контекст.
 *   <li>{@link State#onEnter(GameContext)}, {@link State#onExit(GameContext)}, {@link
 *       State#isComplete(GameContext)} — состояния оперируют контекстом.
 *   <li>{@code engine-network} — создаёт State Projections на основе контекста.
 *   <li>{@code engine-persistence} — сериализует контекст для снапшотов.
 * </ul>
 *
 * <h3>Контракты и инварианты:</h3>
 *
 * <ul>
 *   <li><b>Потокобезопасность чтения:</b> {@code GameContext} должен быть потокобезопасным для
 *       чтения, так как к нему могут одновременно обращаться разные компоненты (валидатор действий,
 *       таймеры, сетевой слой, Event Bus).
 *   <li><b>Атомарность изменений:</b> изменения контекста (перемещение сущностей, установка
 *       атрибутов) должны происходить атомарно в рамках одного {@link Action#execute(GameContext)}.
 *       Частично применённые изменения недопустимы.
 *   <li><b>Уникальность имён зон:</b> каждая зона в контексте имеет уникальное имя ({@link
 *       Zone#getZoneName()}. Две зоны с одинаковым именем в одном контексте недопустимы.
 *   <li><b>Уникальность ID игроков:</b> каждый игрок в контексте имеет уникальный {@link
 *       Player#getPlayerId()}. Дублирование ID игроков недопустимо.
 * </ul>
 *
 * <h3>Система атрибутов:</h3>
 *
 * <p>{@code GameContext} предоставляет гибкое хранилище ключ-значение ({@link
 * #getAttribute(String)} / {@link #setAttribute(String, Object)}) для хранения произвольных данных
 * игры без изменения интерфейса.
 *
 * <p><b>Примеры использования атрибутов:</b>
 *
 * <ul>
 *   <li>{@code context.setAttribute("trumpSuit", "Hearts")} — козырная масть в Дураке.
 *   <li>{@code context.setAttribute("currentBet", 100)} — текущая ставка в Покере.
 *   <li>{@code context.setAttribute("roundNumber", 3)} — номер раунда.
 *   <li>{@code context.setAttribute("attackResolved", true)} — флаг завершения атаки.
 * </ul>
 *
 * <p><b>Рекомендации:</b>
 *
 * <ul>
 *   <li>Используйте константы или enum для ключей атрибутов, чтобы избежать опечаток: {@code public
 *       static final String ATTR_TRUMP_SUIT = "trumpSuit";}.
 *   <li>При получении значения всегда приводите его к нужному типу: {@code String trump = (String)
 *       context.getAttribute("trumpSuit").orElse(null);}.
 *   <li>Не храните в атрибутах большие объекты (например, полные списки карт) — для этого есть
 *       {@link Zone}.
 * </ul>
 *
 * <h3>Примеры использования:</h3>
 *
 * <pre>{@code
 * // Пример 1: Получение зоны и проверка содержимого
 * Zone<Card> hand = context.getZone("hand_" + player.getId());
 * boolean hasSixOfHearts = hand.getAll().stream()
 *     .anyMatch(c -> c.getId().equals("6H"));
 *
 * // Пример 2: Установка и чтение атрибута
 * context.setAttribute("trumpSuit", "Hearts");
 * String trump = (String) context.getAttribute("trumpSuit").orElse("No trump");
 *
 * // Пример 3: Получение текущего ходящего игрока
 * Player currentPlayer = context.getCurrentPlayer();
 * System.out.println("Ход игрока: " + currentPlayer.getName());
 *
 * // Пример 4: Перебор всех игроков
 * for (Player player : context.getPlayers()) {
 *     Zone<Card> hand = context.getZone("hand_" + player.getId());
 *     System.out.println(player.getName() + " имеет " + hand.size() + " карт");
 * }
 * }</pre>
 *
 * <h3>Связь с другими модулями:</h3>
 *
 * <ul>
 *   <li>{@code engine-fsm}: передаёт контекст состояниям и действиям, обновляет {@link
 *       #getCurrentPlayer()} при смене хода.
 *   <li>{@code engine-network}: читает контекст для создания State Projections (фильтрация зон по
 *       {@link Visibility}).
 *   <li>{@code engine-persistence}: сериализует контекст для снапшотов.
 *   <li>{@code engine-core}: {@code GameContext} агрегирует {@link Player}, {@link Zone}, атрибуты.
 * </ul>
 *
 * @author nxckywhxte
 * @since 0.1.0
 * @see Player
 * @see Zone
 * @see State
 * @see Action
 */
public interface GameContext {
  /**
   * Возвращает игрока, чей сейчас ход.
   *
   * <p>Этот метод используется модулем {@code engine-fsm} и реализациями {@link Action} для
   * проверки, кто имеет право совершать действия в текущий момент.
   *
   * <p><b>Контракт:</b> значение не может быть {@code null}. Если ход ещё не назначен (например, в
   * состоянии раздачи), возвращается игрок, который будет ходить первым после завершения раздачи.
   *
   * @return игрок, чей сейчас ход (не {@code null})
   * @see Player
   */
  Player getCurrentPlayer();

  /**
   * Возвращает список всех участников игры.
   *
   * <p>Список включает всех игроков, независимо от их {@link PlayerStatus} ({@code ACTIVE}, {@code
   * DISCONNECTED}, {@code ELIMINATED}). Для получения только активных игроков используйте
   * фильтрацию по статусу.
   *
   * <p><b>Контракт:</b> возвращаемый список не должен быть {@code null}. Список должен быть
   * неизменяемым (unmodifiable) для предотвращения случайных мутаций извне.
   *
   * @return список всех игроков (не {@code null}, может быть пустым)
   * @see Player
   * @see PlayerStatus
   */
  List<Player> getPlayers();

  /**
   * Возвращает зону по её имени.
   *
   * <p>Зоны идентифицируются уникальными именами ({@link Zone#getZoneName()}, заданными при их
   * создании. Этот метод является основным способом доступа к игровым объектам (картам, фишкам,
   * ресурсам).
   *
   * <p><b>Примеры имён зон:</b> {@code "deck"}, {@code "hand_player1"}, {@code "table"}, {@code
   * "discard"}, {@code "bank"}.
   *
   * <p><b>Контракт:</b>
   *
   * <ul>
   *   <li>Параметр {@code name} не может быть {@code null} или пустой строкой.
   *   <li>Если зона с таким именем не существует, метод должен вернуть {@code null} или выбросить
   *       {@link IllegalArgumentException} (зависит от реализации). Рекомендуется возвращать {@code
   *       null} и документировать это поведение.
   * </ul>
   *
   * @param name уникальное имя зоны (не {@code null})
   * @return зона с указанным именем или {@code null}, если не найдена
   * @see Zone#getZoneName()
   */
  Zone<?> getZone(String name);

  /**
   * Возвращает значение атрибута по ключу.
   *
   * <p>Атрибуты — это гибкое хранилище ключ-значение для произвольных данных игры. Подробнее о
   * системе атрибутов см. Javadoc класса {@code GameContext}.
   *
   * <p><b>Пример использования:</b>
   *
   * <pre>{@code
   * String trump = (String) context.getAttribute("trumpSuit").orElse("No trump");
   * Integer round = (Integer) context.getAttribute("roundNumber").orElse(1);
   * }</pre>
   *
   * <p><b>Контракт:</b>
   *
   * <ul>
   *   <li>Параметр {@code key} не может быть {@code null} или пустой строкой.
   *   <li>Если атрибут не установлен, метод возвращает {@code Optional.empty()}.
   *   <li>Значение атрибута может быть любого типа ({@code Object}). При получении значения
   *       разработчик сам приводит его к нужному типу.
   * </ul>
   *
   * @param key ключ атрибута (не {@code null})
   * @return значение атрибута или {@code Optional.empty()}, если не установлен
   * @see #setAttribute(String, Object)
   */
  Optional<Object> getAttribute(String key);

  /**
   * Устанавливает значение атрибута.
   *
   * <p>Если атрибут с таким ключом уже существует, его значение перезаписывается. Если атрибут не
   * существовал, он создаётся.
   *
   * <p><b>Пример использования:</b>
   *
   * <pre>{@code
   * context.setAttribute("trumpSuit", "Hearts");
   * context.setAttribute("currentBet", 100);
   * context.setAttribute("roundNumber", 3);
   * }</pre>
   *
   * <p><b>Контракт:</b>
   *
   * <ul>
   *   <li>Параметр {@code key} не может быть {@code null} или пустой строкой.
   *   <li>Параметр {@code value} может быть {@code null} — в этом случае атрибут удаляется
   *       (эквивалентно {@code removeAttribute(key)}).
   *   <li>Метод должен быть потокобезопасным.
   * </ul>
   *
   * @param key ключ атрибута (не {@code null})
   * @param value значение атрибута (может быть {@code null} для удаления)
   * @see #getAttribute(String)
   */
  void setAttribute(String key, Object value);
}

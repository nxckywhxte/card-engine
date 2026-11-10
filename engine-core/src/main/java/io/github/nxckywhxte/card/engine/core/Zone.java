package io.github.nxckywhxte.card.engine.core;

import java.util.List;
import java.util.Optional;

/**
 * Контейнер для хранения сущностей {@link Entity} с правилами доступа и порядка.
 *
 * <p>Это одна из ключевых абстракций ядра {@code engine-core}. Зона представляет собой
 * структурированное пространство, в котором находятся игровые объекты: карты в колоде, карты в руке
 * игрока, карты на столе, фишки в банке и т.д.
 *
 * <h3>Философия зон:</h3>
 *
 * <p>В отличие от простых коллекций ({@code List}, {@code Set}), зона — это не просто хранилище, а
 * <b>правило</b>, определяющее:
 *
 * <ul>
 *   <li><b>Кто видит содержимое?</b> — через {@link Visibility} (публично, приватно, частично).
 *   <li><b>В каком порядке?</b> — через {@link Order} (стек, очередь, неупорядоченно).
 *   <li><b>Кому принадлежит?</b> — через {@link #getOwner()} (игрок или общая зона).
 * </ul>
 *
 * <h3>Контракты и инварианты:</h3>
 *
 * <ul>
 *   <li><b>Неизменяемость списка:</b> метод {@link #getAllEntities()} должен возвращать
 *       <b>неизменяемую копию</b> (unmodifiable list) внутреннего состояния зоны. Это предотвращает
 *       случайные мутации извне и гарантирует потокобезопасность при чтении.
 *   <li><b>Потокобезопасность:</b> операции {@link #addEntity(Entity)} и {@link
 *       #removeEntity(Entity)} должны быть потокобезопасными, так как к зоне могут одновременно
 *       обращаться разные компоненты (валидатор действий, таймеры, сетевой слой).
 *   <li><b>Уникальность сущностей:</b> одна и та же сущность (по {@link Entity#getEntityId()}) не
 *       может находиться в двух зонах одновременно. При перемещении сущности из одной зоны в
 *       другую, она сначала удаляется из исходной зоны.
 *   <li><b>Порядок элементов:</b> порядок в списке {@link #getAllEntities()} определяется значением
 *       {@link #getZoneOrder()}:
 *       <ul>
 *         <li>{@link Order#STACK} — последний добавленный элемент первый в списке (LIFO).
 *         <li>{@link Order#QUEUE} — первый добавленный элемент первый в списке (FIFO).
 *         <li>{@link Order#UNORDERED} — порядок не гарантирован.
 *       </ul>
 * </ul>
 *
 * <h3>Примеры использования:</h3>
 *
 * <pre>{@code
 * // Пример 1: Колода для добора карт (стек, публичная, общая)
 * Zone<Card> deck = new StandardZone<>(
 *     "deck",
 *     Optional.empty(),           // нет владельца (общая зона)
 *     Visibility.PUBLIC,          // все видят колоду
 *     Order.STACK                 // берём сверху (LIFO)
 * );
 *
 * // Пример 2: Рука игрока (очередь, приватная, принадлежит игроку)
 * Zone<Card> hand = new StandardZone<>(
 *     "hand",
 *     Optional.of(player1),       // владелец — игрок 1
 *     Visibility.PRIVATE,         // только игрок 1 видит свои карты
 *     Order.QUEUE                 // первая взятая карта первой играется (FIFO)
 * );
 *
 * // Пример 3: Стол (неупорядоченное множество, публичная, общая)
 * Zone<Card> table = new StandardZone<>(
 *     "table",
 *     Optional.empty(),           // общая зона
 *     Visibility.PUBLIC,          // все видят карты на столе
 *     Order.UNORDERED             // порядок не важен, важен состав
 * );
 *
 * // Использование
 * deck.add(sixOfHearts);          // добавляем карту в колоду
 * Card drawn = deck.getAll().get(0); // берём верхнюю карту
 * deck.remove(drawn);             // удаляем из колоды
 * hand.add(drawn);                // добавляем в руку
 * }</pre>
 *
 * <h3>Связь с другими модулями:</h3>
 *
 * <ul>
 *   <li>{@code engine-fsm}: использует зоны для хранения состояния игры ({@code
 *       GameContext.getZone("deck")}).
 *   <li>{@code engine-network}: использует {@link #getZoneVisibility()} и {@link #getOwner()} для
 *       фильтрации данных при создании State Projections (античит).
 *   <li>{@code engine-persistence}: сериализует содержимое зон для сохранения в Event Store.
 * </ul>
 *
 * <h3>Стандартная реализация:</h3>
 *
 * <p>Модуль {@code engine-core} предоставляет готовую реализацию {@code StandardZone}, которая:
 *
 * <ul>
 *   <li>Использует {@code ArrayDeque} для {@link Order#STACK} и {@link Order#QUEUE}.
 *   <li>Использует {@code LinkedHashSet} для {@link Order#UNORDERED}.
 *   <li>Гарантирует потокобезопасность через синхронизацию.
 *   <li>Возвращает unmodifiable list из {@link #getAllEntities()}.
 * </ul>
 *
 * @param <E> тип сущностей, хранящихся в зоне (должен реализовывать {@link Entity})
 * @author nxckywhxte
 * @since 0.1.0
 * @see Entity
 * @see Player
 * @see Visibility
 * @see Order
 * @see io.github.nxckywhxte.card.engine.core.GameContext#getZone(String)
 */
public interface Zone<E extends Entity> {
  /**
   * Возвращает уникальное имя зоны.
   *
   * <p>Имя используется для идентификации зоны в {@link
   * io.github.nxckywhxte.card.engine.core.GameContext} через метод {@code getZone(String name)}.
   * Имя должно быть уникальным в рамках одной игровой сессии.
   *
   * <p><b>Примеры имён:</b> {@code "deck"}, {@code "hand"}, {@code "table"}, {@code "discard"},
   * {@code "bank"}.
   *
   * <p><b>Контракт:</b> значение не может быть {@code null} или пустой строкой.
   *
   * @return уникальное имя зоны (например, {@code "deck"})
   * @see io.github.nxckywhxte.card.engine.core.GameContext#getZone(String)
   */
  String getZoneName();

  /**
   * Возвращает владельца зоны, если зона принадлежит конкретному игроку.
   *
   * <p>Владелец зоны определяет, кто имеет полный доступ к её содержимому (в сочетании с {@link
   * #getZoneVisibility()}.
   *
   * <p><b>Примеры:</b>
   *
   * <ul>
   *   <li>Рука игрока: {@code Optional.of(player1)} — только {@code player1} видит карты.
   *   <li>Общий стол: {@code Optional.empty()} — зона не принадлежит никому конкретно.
   *   <li>Колода: {@code Optional.empty()} — общая зона для всех игроков.
   * </ul>
   *
   * <p><b>Контракт:</b> метод никогда не возвращает {@code null}, только {@code Optional.empty()}
   * для общих зон.
   *
   * @return владелец зоны или {@code Optional.empty()} для общих зон
   * @see Visibility#PRIVATE
   */
  Optional<Player> getOwner();

  /**
   * Возвращает уровень видимости содержимого зоны.
   *
   * <p>Видимость определяет, кто имеет право видеть сущности в этой зоне. Подробнее о семантике
   * каждого уровня см. {@link Visibility}.
   *
   * <p><b>Важно:</b> само ядро ({@code engine-core}) не фильтрует данные на основе этого значения —
   * оно лишь хранит метаданные. Физическое отсечение приватной информации происходит в модуле
   * {@code engine-network} через механизм State Projections.
   *
   * @return уровень видимости зоны (не {@code null})
   * @see Visibility
   */
  Visibility getZoneVisibility();

  /**
   * Возвращает порядок хранения и извлечения сущностей из зоны.
   *
   * <p>Порядок определяет семантику операций "добавить" и "взять", а также порядок элементов в
   * списке, возвращаемом методом {@link #getAllEntities()}. Подробнее о семантике каждого порядка
   * см. {@link Order}.
   *
   * <p><b>Контракт:</b> значение не может быть {@code null} и не может меняться после создания
   * зоны.
   *
   * @return порядок зоны (не {@code null})
   * @see Order
   */
  Order getZoneOrder();

  /**
   * Добавляет сущность в зону.
   *
   * <p>Сущность добавляется в соответствии с {@link #getZoneOrder()}:
   *
   * <ul>
   *   <li>{@link Order#STACK}: сущность добавляется "сверху" (последней в списке {@link
   *       #getAllEntities()}.
   *   <li>{@link Order#QUEUE}: сущность добавляется "в хвост" (последней в списке {@link
   *       #getAllEntities()}).
   *   <li>{@link Order#UNORDERED}: сущность добавляется в коллекцию без гарантий порядка.
   * </ul>
   *
   * <p><b>Контракт:</b>
   *
   * <ul>
   *   <li>Параметр {@code entity} не может быть {@code null}.
   *   <li>Одна и та же сущность (по {@link Entity#getEntityId()} не может быть добавлена в зону
   *       дважды. Повторное добавление должно игнорироваться или выбрасывать исключение (зависит от
   *       реализации).
   *   <li>Метод должен быть потокобезопасным.
   * </ul>
   *
   * @param entity сущность для добавления (не {@code null})
   * @throws NullPointerException если {@code entity} равен {@code null}
   * @throws IllegalArgumentException если сущность уже находится в этой зоне
   * @see #removeEntity(Entity)
   */
  void addEntity(E entity);

  /**
   * Удаляет сущность из зоны.
   *
   * <p>Сущность удаляется из внутреннего хранилища зоны. После удаления сущность больше не
   * возвращается методом {@link #getAllEntities()}.
   *
   * <p><b>Контракт:</b>
   *
   * <ul>
   *   <li>Параметр {@code entity} не может быть {@code null}.
   *   <li>Если сущность не находится в зоне, метод должен игнорировать вызов (не выбрасывать
   *       исключение).
   *   <li>Метод должен быть потокобезопасным.
   * </ul>
   *
   * @param entity сущность для удаления (не {@code null})
   * @throws NullPointerException если {@code entity} равен {@code null}
   * @see #addEntity(Entity)
   */
  void removeEntity(E entity);

  /**
   * Возвращает неизменяемый список всех сущностей в зоне.
   *
   * <p>Список отсортирован в соответствии с {@link #getZoneOrder()}:
   *
   * <ul>
   *   <li>{@link Order#STACK}: первый элемент списка — последний добавленный (верх стека).
   *   <li>{@link Order#QUEUE}: первый элемент списка — первый добавленный (голова очереди).
   *   <li>{@link Order#UNORDERED}: порядок не гарантирован.
   * </ul>
   *
   * <p><b>Контракт:</b>
   *
   * <ul>
   *   <li>Возвращаемый список должен быть <b>неизменяемым</b> (unmodifiable). Попытка изменить
   *       список (добавить, удалить, отсортировать) должна выбрасывать {@link
   *       UnsupportedOperationException}.
   *   <li>Список должен быть <b>снимком</b> (snapshot) состояния зоны на момент вызова. Последующие
   *       изменения зоны не должны влиять на уже возвращённый список.
   *   <li>Метод должен быть потокобезопасным.
   * </ul>
   *
   * <p><b>Важно:</b> этот метод возвращает <b>все</b> сущности зоны, независимо от {@link
   * #getZoneVisibility()}. Фильтрация для конкретных игроков происходит в модуле {@code
   * engine-network} через State Projections.
   *
   * @return неизменяемый список всех сущностей в зоне (не {@code null}, может быть пустым)
   * @see #zoneSize()
   * @see #isEmpty()
   */
  List<E> getAllEntities();

  /**
   * Возвращает текущее количество сущностей в зоне.
   *
   * <p>Этот метод полезен для быстрой проверки размера зоны без получения полного списка через
   * {@link #getAllEntities()}.
   *
   * <p><b>Контракт:</b> возвращаемое значение всегда {@code >= 0}.
   *
   * @return количество сущностей в зоне (неотрицательное число)
   * @see #getAllEntities()
   * @see #isEmpty()
   */
  int zoneSize();

  /**
   * Проверяет, пуста ли зона (не содержит сущностей).
   *
   * <p>Этот метод является удобным сокращением для {@code size() == 0}.
   *
   * <p><b>Пример использования:</b>
   *
   * <pre>{@code
   * if (deck.isEmpty()) {
   *     // Колода пуста — нужно перемешать сброс или завершить игру
   * }
   * }</pre>
   *
   * @return {@code true}, если зона не содержит сущностей; {@code false} иначе
   * @see #zoneSize()
   */
  default boolean isEmpty() {
    return zoneSize() == 0;
  }
}

package io.github.nxckywhxte.card.engine.core;

import java.util.List;

/**
 * Представляет фазу (состояние) игры.
 *
 * <p>Это одна из семи базовых абстракций ядра {@code engine-core}. Состояние определяет: какие
 * действия доступны игрокам в текущий момент, что происходит при входе в фазу и при выходе из неё,
 * а также условие автоматического завершения фазы.
 *
 * <h3>Роль в архитектуре:</h3>
 *
 * <p>{@code State} является ключевым компонентом конечного автомата (FSM), реализуемого модулем
 * {@code engine-fsm}. Модуль {@code engine-fsm} управляет переходами между состояниями, а сами
 * состояния содержат бизнес-логику конкретной фазы игры.
 *
 * <h3>Жизненный цикл состояния:</h3>
 *
 * <ol>
 *   <li><b>Вход:</b> FSM вызывает {@link #onEnter(GameContext)} — инициализация фазы (раздача карт,
 *       запуск таймера, установка атрибутов).
 *   <li><b>Активная фаза:</b> игроки совершают {@link Action действия}, доступные согласно {@link
 *       #getAvailableActions()}.
 *   <li><b>Проверка завершения:</b> после каждого действия FSM вызывает {@link
 *       #isComplete(GameContext)}.
 *   <li><b>Выход:</b> если {@code isComplete()} вернул {@code true}, FSM вызывает {@link
 *       #onExit(GameContext)} — очистка временных данных, логирование — и переходит к следующему
 *       состоянию.
 * </ol>
 *
 * <h3>Контракты и инварианты:</h3>
 *
 * <ul>
 *   <li><b>Имя состояния:</b> {@link #getName()} должно возвращать уникальное в рамках игры имя
 *       (например, {@code "DealingState"}, {@code "AttackPhase"}). Имя используется в логах,
 *       реплеях и отладке.
 *   <li><b>Доступные действия:</b> {@link #getAvailableActions()} возвращает <b>whitelist</b>
 *       классов действий, разрешённых в этом состоянии. Модуль {@code engine-fsm} использует этот
 *       список для быстрой фильтрации недопустимых действий <b>до</b> вызова {@code
 *       Action.isValid()}.
 *   <li><b>Побочные эффекты в onEnter/onExit:</b> в отличие от {@code Action.isValid()}, методы
 *       {@link #onEnter(GameContext)} и {@link #onExit(GameContext)} <b>могут и должны</b> изменять
 *       {@code GameContext} — устанавливать атрибуты, перемещать сущности между зонами, менять
 *       статусы игроков.
 *   <li><b>Детерминированность isComplete:</b> метод {@link #isComplete(GameContext)} должен быть
 *       чистой функцией (без побочных эффектов) и детерминированным: при одинаковом контексте
 *       всегда возвращать одинаковый результат.
 * </ul>
 *
 * <h3>Иерархические состояния:</h3>
 *
 * <p>В сложных играх (например, "Дурак" с подкидыванием) состояния могут быть вложенными.
 * Родительское состояние ({@code AttackPhase}) содержит дочерние ({@code InitialAttackState},
 * {@code AdditionalAttackState}). Дочерние состояния наследуют доступные действия и логику перехода
 * от родителя.
 *
 * <p>Поддержка иерархии опциональна и реализуется через дополнительные методы (будут добавлены в
 * следующих версиях через систему атрибутов или отдельный интерфейс {@code CompositeState}).
 *
 * <h3>Примеры реализации:</h3>
 *
 * <pre>{@code
 * // Пример 1: Состояние раздачи карт
 * public class DealingState implements State {
 *     @Override
 *     public String getName() { return "DealingState"; }
 *
 *     @Override
 *     public List<Class<? extends Action>> getAvailableActions() {
 *         return List.of(); // Во время раздачи игроки не совершают действий
 *     }
 *
 *     @Override
 *     public void onEnter(GameContext context) {
 *         // Раздать по 6 карт каждому игроку
 *         // Определить козырь
 *         // Найти игрока с младшим козырем — он ходит первым
 *         context.setAttribute("trumpSuit", determineTrump(context));
 *     }
 *
 *     @Override
 *     public void onExit(GameContext context) {
 *         // Логирование завершения раздачи
 *     }
 *
 *     @Override
 *     public boolean isComplete(GameContext context) {
 *         // Все игроки получили по 6 карт
 *         return context.getPlayers().stream()
 *             .allMatch(p -> context.getZone("hand_" + p.getId()).size() == 6);
 *     }
 * }
 *
 * // Пример 2: Состояние атаки в Дураке
 * public class AttackState implements State {
 *     @Override
 *     public String getName() { return "AttackState"; }
 *
 *     @Override
 *     public List<Class<? extends Action>> getAvailableActions() {
 *         return List.of(AttackAction.class, EndTurnAction.class);
 *     }
 *
 *     @Override
 *     public void onEnter(GameContext context) {
 *         // Запустить таймер на ход (30 секунд)
 *         context.setAttribute("attackTimerStarted", Instant.now());
 *     }
 *
 *     @Override
 *     public boolean isComplete(GameContext context) {
 *         // Атакующий сказал "Бито" или защищающийся взял карты
 *         return Boolean.TRUE.equals(context.getAttribute("attackResolved").orElse(false));
 *     }
 * }
 *
 * // Пример 3: Состояние конца игры
 * public class GameOverState implements State {
 *     @Override
 *     public String getName() { return "GameOverState"; }
 *
 *     @Override
 *     public List<Class<? extends Action>> getAvailableActions() {
 *         return List.of(); // В конце игры действий нет
 *     }
 *
 *     @Override
 *     public boolean isComplete(GameContext context) {
 *         return true; // Конечное состояние никогда не завершается автоматически
 *     }
 * }
 * }</pre>
 *
 * <h3>Связь с другими модулями:</h3>
 *
 * <ul>
 *   <li>{@code engine-fsm}: управляет жизненным циклом состояний, вызывает {@code onEnter}/{@code
 *       onExit}/{@code isComplete()}, обрабатывает переходы.
 *   <li>{@code engine-core}: {@code State} оперирует {@link GameContext}, {@link Action}, {@link
 *       Player}, {@link Zone}.
 *   <li>{@code engine-persistence}: генерирует событие {@code StateTransitionedEvent} при каждом
 *       переходе между состояниями.
 * </ul>
 *
 * @author nxckywhxte
 * @since 0.1.0
 * @see Action
 * @see GameContext
 * @see Player
 */
public interface State {

  /**
   * Возвращает уникальное имя состояния.
   *
   * <p>Имя используется в логах, реплеях, отладке и при сериализации в {@code engine-persistence}.
   * Рекомендуется использовать формат PascalCase с суффиксом {@code State} или {@code Phase}
   * (например, {@code "DealingState"}, {@code "AttackPhase"}).
   *
   * <p><b>Контракт:</b> значение не может быть {@code null} или пустой строкой. Имя должно быть
   * уникальным в рамках одной игры.
   *
   * @return уникальное имя состояния (например, {@code "AttackState"})
   */
  String getName();

  /**
   * Возвращает список классов действий, доступных в этом состоянии.
   *
   * <p>Это <b>whitelist</b>: только действия из этого списка могут быть выполнены игроками, пока
   * игра находится в данном состоянии. Модуль {@code engine-fsm} использует этот список для быстрой
   * фильтрации недопустимых действий <b>до</b> вызова {@link Action#isValid(GameContext)}.
   *
   * <p><b>Примеры:</b>
   *
   * <ul>
   *   <li>В состоянии {@code AttackState}: {@code [AttackAction.class, EndTurnAction.class]}.
   *   <li>В состоянии {@code DefendState}: {@code [DefendAction.class, TakeCardsAction.class]}.
   *   <li>В состоянии {@code DealingState}: {@code []} (игроки не совершают действий).
   * </ul>
   *
   * <p><b>Контракт:</b> возвращаемый список не должен быть {@code null}. Если действий нет,
   * возвращайте пустой список {@code List.of()}.
   *
   * @return список доступных классов действий (не {@code null}, может быть пустым)
   * @see Action
   */
  List<Class<? extends Action>> getAvailableActions();

  /**
   * Вызывается при входе в состояние.
   *
   * <p>Здесь выполняется инициализация фазы: раздача карт, запуск таймеров, установка атрибутов
   * контекста, определение первого ходящего игрока и т.д.
   *
   * <p><b>Важно:</b> в отличие от {@link Action#isValid(GameContext)}, этот метод <b>может и
   * должен</b> изменять {@code GameContext} — устанавливать атрибуты, перемещать сущности между
   * зонами, менять статусы игроков.
   *
   * <p><b>Контракт:</b>
   *
   * <ul>
   *   <li>Метод вызывается ровно один раз при переходе в это состояние.
   *   <li>Метод не должен выбрасывать непроверенные исключения. В случае ошибки FSM перейдёт в
   *       аварийное состояние {@code ErrorState}.
   *   <li>Метод должен быть идемпотентным: повторный вызов с тем же контекстом не должен приводить
   *       к непредсказуемым последствиям (хотя на практике вызывается только один раз).
   * </ul>
   *
   * @param context текущее состояние игры (не {@code null})
   * @see #onExit(GameContext)
   * @see GameContext#setAttribute(String, Object)
   */
  void onEnter(GameContext context);

  /**
   * Вызывается при выходе из состояния.
   *
   * <p>Здесь выполняется очистка временных данных, логирование завершения фазы, отмена таймеров и
   * т.д.
   *
   * <p><b>Контракт:</b>
   *
   * <ul>
   *   <li>Метод вызывается ровно один раз при переходе из этого состояния в другое.
   *   <li>Метод может изменять {@code GameContext} (например, сбрасывать временные атрибуты).
   *   <li>Метод не должен выбрасывать исключения — FSM должен гарантированно завершить переход.
   * </ul>
   *
   * @param context текущее состояние игры (не {@code null})
   * @see #onEnter(GameContext)
   */
  void onExit(GameContext context);

  /**
   * Проверяет, завершено ли состояние и можно ли перейти к следующему.
   *
   * <p>Этот метод вызывается модулем {@code engine-fsm} <b>после каждого действия</b> игрока. Если
   * метод возвращает {@code true}, FSM инициирует переход к следующему состоянию (вызывает {@link
   * #onExit(GameContext)}, определяет следующее состояние через Transition Table и вызывает {@code
   * onEnter()} для нового состояния).
   *
   * <p><b>Примеры условий завершения:</b>
   *
   * <ul>
   *   <li>{@code DealingState}: все игроки получили по 6 карт.
   *   <li>{@code AttackState}: атакующий сказал "Бито" или защищающийся взял карты.
   *   <li>{@code DefendState}: все атакующие карты побиты или защищающийся взял.
   *   <li>{@code GameOverState}: {@code return true} (конечное состояние).
   * </ul>
   *
   * <p><b>Контракт:</b>
   *
   * <ul>
   *   <li>Метод должен быть <b>чистой функцией</b>: не изменять {@code context}, не иметь побочных
   *       эффектов.
   *   <li>Метод должен быть <b>детерминированным</b>: при одинаковом контексте всегда возвращать
   *       одинаковый результат.
   *   <li>Метод не должен выбрасывать исключения.
   * </ul>
   *
   * @param context текущее состояние игры (не {@code null})
   * @return {@code true}, если состояние завершено и нужен переход; {@code false} иначе
   */
  boolean isComplete(GameContext context);
}

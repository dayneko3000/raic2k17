import model.ActionType;
import model.Game;
import model.Move;
import model.Player;
import model.TerrainType;
import model.Vehicle;
import model.VehicleType;
import model.VehicleUpdate;
import model.WeatherType;
import model.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static model.VehicleType.ARRV;
import static model.VehicleType.FIGHTER;
import static model.VehicleType.HELICOPTER;
import static model.VehicleType.IFV;
import static model.VehicleType.TANK;


@SuppressWarnings({"UnsecureRandomNumberGeneration", "FieldCanBeLocal", "unused", "OverlyLongMethod"})
public final class MyStrategy implements Strategy {
    private static final int AIR = 1, GROUND = 2;
    private final Map<Long, Vehicle> vehicleById = new HashMap<>();
    private final Map<Long, Integer> updateTickByVehicleId = new HashMap<>();
    private int orderY = 120;
    private Random random;
    private TerrainType[][] terrainTypeByCellXY;
    private WeatherType[][] weatherTypeByCellXY;
    private Player me;
    private World world;
    private Game game;
    private Move move;
    private Queue<Consumer<Move>> delayedMoves = new ArrayDeque<>();
    private int ping = 20;
    private int net = 10;
    private Point groundVector = new Point(1, 0);
    private Point airVector = new Point(1, 0);
    private Point tankMass, arrvMass, helicopterMass, fighterMass, ifvMass, nearestGroundEnemy, nearestAirEnemy, groundMass, airMass;
    private int center = 120;
    private Point airPoint1 = new Point(orderY, orderY), airPoint2 = new Point(200, orderY);
    private Point groundPoint1 = new Point(40, orderY), groundPoint2 = new Point((int) center, (int) orderY), groundPoint3 = new Point(190, orderY);
    private boolean orderedHorizontal = false;
    private boolean orderedVertical = false;
    private boolean scaled = false;
    private boolean assigned = false;
    private boolean shifted = false;
    private boolean horizontaled = false;
    private boolean discaledVertical = false;
    private boolean discaledHorizonal = false;
    private boolean start = false;
    private boolean fighterSelected = false;
    private int fighterSelectedTick = 0;
    private boolean init = false;
    private boolean battleScaling = false;
    private boolean battleRotating = false;
    private boolean battleDiscaling = false;
    private boolean nuclearScale = false;
    private boolean nuclearDiscale = false;
    private int battleMovingGroup = 0;
    private int selectedGroup = 0;
    private double factor = 1.1;
    private boolean groupsSeted;

    /**
     * Основной метод стратегии, осуществляющий управление армией. Вызывается каждый тик.
     *
     * @param me    Информация о вашем игроке.
     * @param world Текущее состояние мира.
     * @param game  Различные игровые константы.
     * @param move  Результатом работы метода является изменение полей данного объекта.
     */
    @Override
    public void move(Player me, World world, Game game, Move move) {
        initializeStrategy(world, game);
        initializeTick(me, world, game, move);

        if (me.getRemainingActionCooldownTicks() > 0) {
            return;
        }
        if (nuclearScale) {
            if (!fireInTheHole(world.getOpponentPlayer())) {
                executeDelayedMove();
                nuclearScale = false;
            }
            return;
        }
        if (nuclearDiscale) {
            if (enough(battleMovingGroup)) {
                executeDelayedMove();
                nuclearDiscale = false;
            }
            return;
        }

        if (battleScaling) {
            if (enough(battleMovingGroup)) {
                executeDelayedMove();
                battleScaling = false;
            }
            return;
        }
        if (battleRotating) {
            if (enough(battleMovingGroup)) {
                executeDelayedMove();
                battleRotating = false;
            }
            return;
        }
        if (battleDiscaling) {
            if (enough(battleMovingGroup)) {
                executeDelayedMove();
                battleDiscaling = false;
            }
            return;
        }

        if (executeDelayedMove()) {
            return;
        }

        ready();
        if (start) {
            if (fireInTheHole(world.getOpponentPlayer())) {
                Point enemyNuclearSrtike = new Point(world.getOpponentPlayer().getNextNuclearStrikeX(), world.getOpponentPlayer().getNextNuclearStrikeY());
                if (distance(enemyNuclearSrtike, groundMass) < 20 + game.getTacticalNuclearStrikeRadius() && distance(enemyNuclearSrtike, airMass) < 20 + game.getTacticalNuclearStrikeRadius()) {
                    selectVehicleType(null);
                    nuclearEnemyAttack(enemyNuclearSrtike, GROUND);
                } else {
                    if (distance(enemyNuclearSrtike, airMass) < 20 + game.getTacticalNuclearStrikeRadius()) {
                        selectGroup(AIR);
                        nuclearEnemyAttack(enemyNuclearSrtike, AIR);
                    }
                    if (distance(enemyNuclearSrtike, groundMass) < 20 + game.getTacticalNuclearStrikeRadius()) {
                        selectGroup(GROUND);
                        nuclearEnemyAttack(enemyNuclearSrtike, GROUND);
                    }
                }
                return;
            }
            if (!nuclearAttack()) {
                go();
            }
        } else {
            nuclearAttack();
        }

        executeDelayedMove();
    }

    /**
     * Инциализируем стратегию.
     * <p>
     * Для этих целей обычно можно использовать конструктор, однако в данном случае мы хотим инициализировать генератор
     * случайных чисел значением, полученным от симулятора игры.
     */
    private void initializeStrategy(World world, Game game) {
        if (random == null) {
            random = new Random(game.getRandomSeed());

            terrainTypeByCellXY = world.getTerrainByCellXY();
            weatherTypeByCellXY = world.getWeatherByCellXY();
        }
    }

    /**
     * Сохраняем все входные данные в полях класса для упрощения доступа к ним, а также актуализируем сведения о каждой
     * технике и времени последнего изменения её состояния.
     */
    private void initializeTick(Player me, World world, Game game, Move move) {
        this.me = me;
        this.world = world;
        this.game = game;
        this.move = move;

        for (Vehicle vehicle : world.getNewVehicles()) {
            vehicleById.put(vehicle.getId(), vehicle);
            if (vehicle.getPlayerId() == me.getId()) {
                updateTickByVehicleId.put(vehicle.getId(), world.getTickIndex());
            }
        }

        for (VehicleUpdate vehicleUpdate : world.getVehicleUpdates()) {
            long vehicleId = vehicleUpdate.getId();
            Vehicle vehicle = vehicleById.get(vehicleId);

            if (vehicleUpdate.getDurability() == 0) {
                vehicleById.remove(vehicleId);
                updateTickByVehicleId.remove(vehicleId);
            } else {
                if (vehicleUpdate.getX() != vehicle.getX() || vehicleUpdate.getY() != vehicle.getY() || vehicleUpdate.getGroups().length != vehicle.getGroups().length) {
                    vehicleById.put(vehicleId, new Vehicle(vehicleById.get(vehicleId), vehicleUpdate));
                    updateTickByVehicleId.put(vehicleId, world.getTickIndex());
                }
            }
        }


        tankMass = getMassOfVehicle(Ownership.ALLY, TANK);
        helicopterMass = getMassOfVehicle(Ownership.ALLY, HELICOPTER);
        ifvMass = getMassOfVehicle(Ownership.ALLY, IFV);
        fighterMass = getMassOfVehicle(Ownership.ALLY, FIGHTER);
        arrvMass = getMassOfVehicle(Ownership.ALLY, ARRV);
        groundMass = getMassOfVehicle(Ownership.ALLY, ARRV, TANK, IFV);
        airMass = getMassOfVehicle(Ownership.ALLY, HELICOPTER, FIGHTER);

        if (!init) {
            if (tankMass.getX() > world.getWidth() / 2) {
                orderY = (int) world.getHeight() - 100;
                airPoint1 = new Point(orderY, orderY);
                airPoint2 = new Point(world.getWidth() - 200, orderY);
                groundPoint1 = new Point(world.getWidth() - 40, orderY);
                groundPoint2 = new Point(world.getWidth() - 120, orderY);
                groundPoint3 = new Point(world.getWidth() - 190, orderY);

                groundVector = new Point(world.getWidth() / 2 - tankMass.x, 0);
                airVector = new Point(world.getWidth() / 2 - tankMass.x, 0);
            }
            setGroups();
            init = true;
        }

        nearestGroundEnemy = null;
        nearestAirEnemy = null;
        final double[] distance = {99999, 99999};
        streamVehicles(
                Ownership.ENEMY, null
        ).forEach((x) ->
        {
            if (distance[0] > distance(groundMass, new Point((int) x.getX(), (int) x.getY()))) {
                distance[0] = distance(groundMass, new Point((int) x.getX(), (int) x.getY()));
                nearestGroundEnemy = new Point((int) x.getX(), (int) x.getY());
            }
            if (distance[1] > distance(airMass, new Point((int) x.getX(), (int) x.getY()))) {
                distance[1] = distance(airMass, new Point((int) x.getX(), (int) x.getY()));
                nearestAirEnemy = new Point((int) x.getX(), (int) x.getY());
            }
        });

        if (nearestGroundEnemy == null) {
            distance[0] = 99999;
            streamVehicles(
                    Ownership.ENEMY, null
            ).forEach((x) ->
            {
                if (distance[0] > distance(groundMass, new Point((int) x.getX(), (int) x.getY()))) {
                    distance[0] = distance(groundMass, new Point((int) x.getX(), (int) x.getY()));
                    nearestGroundEnemy = new Point((int) x.getX(), (int) x.getY());
                }
            });
        }
    }

    private void fillMap() {

    }

    /**
     * Достаём отложенное действие из очереди и выполняем его.
     *
     * @return Возвращает {@code true}, если и только если отложенное действие было найдено и выполнено.
     */
    private boolean executeDelayedMove() {
        Consumer<Move> delayedMove = delayedMoves.poll();
        if (delayedMove == null) {
            return false;
        }

        delayedMove.accept(move);
        return true;
    }

    private double getSpeed(VehicleType type) {
        switch (type) {
            case ARRV:
                return game.getArrvSpeed();
            case FIGHTER:
                return game.getFighterSpeed();
            case HELICOPTER:
                return game.getHelicopterSpeed();
            case IFV:
                return game.getIfvSpeed();
            default:
                return game.getTankSpeed();
        }
    }

    private boolean fireInTheHole(Player p) {
        return p.getRemainingNuclearStrikeCooldownTicks() >= game.getBaseTacticalNuclearStrikeCooldown() - game.getTacticalNuclearStrikeDelay();
    }

    private boolean enough(int group) {
        return streamVehicles(Ownership.ALLY).allMatch(
                vehicle -> world.getTickIndex() - updateTickByVehicleId.get(vehicle.getId()) > 5 || vehicle.getGroups().length == 0 || vehicle.getGroups()[0] != group
        );
    }

    private void ready() {
        if (!orderedHorizontal) {
            double air1 = Math.max(Math.abs(airPoint1.x - helicopterMass.x) / (game.getHelicopterSpeed() * 0.6), Math.abs(airPoint2.x - fighterMass.x) / (game.getFighterSpeed() * 0.6));
            double air2 = Math.max(Math.abs(airPoint2.x - helicopterMass.x) / (game.getHelicopterSpeed() * 0.6), Math.abs(airPoint1.x - fighterMass.x) / (game.getFighterSpeed() * 0.6));
            double ground1 = Math.max(Math.abs(groundPoint1.x - tankMass.x) / (game.getTankSpeed() * 0.6), Math.max(Math.abs(groundPoint2.x - ifvMass.x) / (game.getIfvSpeed() * 0.6), Math.abs(groundPoint3.x - arrvMass.x) / (game.getArrvSpeed() * 0.6)));
            double ground2 = Math.max(Math.abs(groundPoint1.x - tankMass.x) / (game.getTankSpeed() * 0.6), Math.max(Math.abs(groundPoint3.x - ifvMass.x) / (game.getIfvSpeed() * 0.6), Math.abs(groundPoint2.x - arrvMass.x) / (game.getArrvSpeed() * 0.6)));
            double ground3 = Math.max(Math.abs(groundPoint2.x - tankMass.x) / (game.getTankSpeed() * 0.6), Math.max(Math.abs(groundPoint1.x - ifvMass.x) / (game.getIfvSpeed() * 0.6), Math.abs(groundPoint3.x - arrvMass.x) / (game.getArrvSpeed() * 0.6)));
            double ground4 = Math.max(Math.abs(groundPoint2.x - tankMass.x) / (game.getTankSpeed() * 0.6), Math.max(Math.abs(groundPoint3.x - ifvMass.x) / (game.getIfvSpeed() * 0.6), Math.abs(groundPoint1.x - arrvMass.x) / (game.getArrvSpeed() * 0.6)));
            double ground5 = Math.max(Math.abs(groundPoint3.x - tankMass.x) / (game.getTankSpeed() * 0.6), Math.max(Math.abs(groundPoint1.x - ifvMass.x) / (game.getIfvSpeed() * 0.6), Math.abs(groundPoint2.x - arrvMass.x) / (game.getArrvSpeed() * 0.6)));
            double ground6 = Math.max(Math.abs(groundPoint3.x - tankMass.x) / (game.getTankSpeed() * 0.6), Math.max(Math.abs(groundPoint2.x - ifvMass.x) / (game.getIfvSpeed() * 0.6), Math.abs(groundPoint1.x - arrvMass.x) / (game.getArrvSpeed() * 0.6)));
            double groundMinDist = Math.min(Math.min(Math.min(Math.min(Math.min(ground1, ground2), ground3), ground4), ground5), ground6);
            if (air1 == Math.min(air1, air2)) {
                selectVehicleType(HELICOPTER);
                moveHorizontal(helicopterMass, airPoint1, game.getHelicopterSpeed() * 0.6);
                selectVehicleType(FIGHTER);
                moveHorizontal(fighterMass, airPoint2, game.getFighterSpeed() * 0.6);
            } else {
                selectVehicleType(HELICOPTER);
                moveHorizontal(helicopterMass, airPoint2, game.getHelicopterSpeed() * 0.6);
                selectVehicleType(FIGHTER);
                moveHorizontal(fighterMass, airPoint1, game.getFighterSpeed() * 0.6);
            }
            if (ground1 == groundMinDist) {
                horizontalGroundMove(groundPoint1, groundPoint2, groundPoint3);
            }
            if (ground2 == groundMinDist) {
                horizontalGroundMove(groundPoint1, groundPoint3, groundPoint2);
            }
            if (ground3 == groundMinDist) {
                horizontalGroundMove(groundPoint2, groundPoint1, groundPoint3);
            }
            if (ground4 == groundMinDist) {
                horizontalGroundMove(groundPoint2, groundPoint3, groundPoint1);
            }
            if (ground5 == groundMinDist) {
                horizontalGroundMove(groundPoint3, groundPoint1, groundPoint2);
            }
            if (ground6 == groundMinDist) {
                horizontalGroundMove(groundPoint3, groundPoint2, groundPoint1);
            }
            orderedHorizontal = true;
            return;
        }
        if (!orderedVertical && enough(GROUND) && enough(AIR)) {
            selectVehicleType(FIGHTER);
            moveVertical(fighterMass, airPoint1, game.getFighterSpeed() * 0.6);
            selectVehicleType(HELICOPTER);
            moveVertical(helicopterMass, airPoint2, game.getHelicopterSpeed() * 0.6);
            verticalGroundMove(groundPoint1, groundPoint2, groundPoint3);
            orderedVertical = true;
            return;
        }

        if (!scaled && enough(GROUND) && enough(AIR)) {
            for (int row : getRowList(ARRV)) {
                selectRow(row);
                shiftVertical(((row - orderY) * 2));
            }
            scaled = true;
            return;
        }
        if (!shifted && enough(GROUND) && enough(AIR)) {
            selectVehicleType(TANK);
            shiftVertical(5);
            selectVehicleType(IFV);
            shiftVertical(-5);
            selectVehicleType(FIGHTER);
            shiftVertical(5);
            shifted = true;
            return;
        }
        if (enough(GROUND) && enough(AIR) && !horizontaled) {
            selectVehicleType(TANK);
            moveHorizontal(tankMass, groundPoint2, game.getTankSpeed() * 0.6);
            selectVehicleType(IFV);
            moveHorizontal(ifvMass, groundPoint2, game.getIfvSpeed() * 0.6);
            selectVehicleType(ARRV);
            moveHorizontal(arrvMass, groundPoint2, game.getArrvSpeed() * 0.6);
            selectVehicleType(HELICOPTER);
            moveHorizontal(helicopterMass, groundPoint2, game.getHelicopterSpeed() * 0.6);
            selectVehicleType(FIGHTER);
            moveHorizontal(fighterMass, groundPoint2, game.getFighterSpeed() * 0.6);
            horizontaled = true;

            return;
        }

        if (!discaledVertical && enough(GROUND) && enough(AIR)) {
            for (int row : getRowList(ARRV, TANK, IFV, HELICOPTER, FIGHTER)) {
                selectRow(row);
                shiftVertical(-(row - orderY) / 2);
            }
            discaledVertical = true;
            return;
        }
        if (!discaledHorizonal && enough(GROUND) && enough(AIR)) {
            boolean shift = false;
            List<Integer> columns = getColumnList(ARRV, TANK, IFV, HELICOPTER, FIGHTER);
            Collections.sort(columns);
            for (int column : columns) {
                selectColumn(column);
                moveVector(new Point(-(column - center) / 2, shift ? -5 : 0), game.getTankSpeed() * 0.6);
                shift = !shift;
            }
            discaledHorizonal = true;
            return;
        }
        if (!start) {
            if (discaledHorizonal && enough(GROUND) && enough(AIR)) {
                start = true;
                fighterSelected = true;
            }
        }
    }

    private void setGroups() {
        for (VehicleType type : VehicleType.values()) {
            selectVehicleType(type);
            assign(type == HELICOPTER || type == FIGHTER ? AIR : GROUND);
        }
    }

    private void assign(int group) {
        delayedMoves.add((move) ->
        {
            move.setAction(ActionType.ASSIGN);
            move.setGroup(group);
        });
    }

    private void selectRow(int y) {
        delayedMoves.add((move) ->
        {
            move.setAction(ActionType.CLEAR_AND_SELECT);
            move.setRight(world.getWidth());
            move.setTop(y - 1);
            move.setBottom(y + 1);
        });
    }

    private void selectColumn(int x) {
        delayedMoves.add((move) ->
        {
            move.setAction(ActionType.CLEAR_AND_SELECT);
            move.setBottom(world.getHeight());
            move.setRight(x + 1);
            move.setLeft(x - 1);
        });
    }

    private List<Integer> getRowList(VehicleType... types) {
        Set<Integer> result = streamVehicles(Ownership.ALLY, types).map((v) -> (int) v.getY()).collect(Collectors.toSet());
        return new ArrayList<>(result);
    }

    private List<Integer> getColumnList(VehicleType... types) {
        Set<Integer> result = streamVehicles(Ownership.ALLY, types).map((v) -> (int) v.getX()).collect(Collectors.toSet());
        return new ArrayList<>(result);
    }

    private void verticalGroundMove(Point p1, Point p2, Point p3) {
        selectVehicleType(TANK);
        moveVertical(tankMass, p1, game.getTankSpeed() * 0.6);
        selectVehicleType(IFV);
        moveVertical(ifvMass, p2, game.getIfvSpeed() * 0.6);
        selectVehicleType(ARRV);
        moveVertical(arrvMass, p3, game.getArrvSpeed() * 0.6);
    }

    private void horizontalGroundMove(Point p1, Point p2, Point p3) {
        selectVehicleType(TANK);
        moveHorizontal(tankMass, p1, game.getTankSpeed() * 0.6);
        selectVehicleType(IFV);
        moveHorizontal(ifvMass, p2, game.getIfvSpeed() * 0.6);
        selectVehicleType(ARRV);
        moveHorizontal(arrvMass, p3, game.getArrvSpeed() * 0.6);
    }


    private void moveFromTo(Point from, Point to, Double maxSpeed) {
        delayedMoves.add(move ->
        {
            move.setAction(ActionType.MOVE);
            move.setX(to.getX() - from.getX());
            move.setY(to.getY() - from.getY());
            move.setMaxSpeed(maxSpeed);
        });
    }

    private void moveHorizontal(Point from, Point to, Double maxSpeed) {

        delayedMoves.add(move ->
        {
            move.setAction(ActionType.MOVE);
            move.setX(to.getX() - from.getX());
            if (maxSpeed != null) {
                if (Math.abs(to.getX() - from.getX()) > maxSpeed) {
                    move.setMaxSpeed(maxSpeed);
                } else {
                    move.setMaxSpeed(Math.abs(to.getX() - from.getX()));
                }
            }

        });
    }

    private void moveVertical(Point from, Point to, Double maxSpeed) {
        delayedMoves.add(move ->
        {
            move.setAction(ActionType.MOVE);
            move.setY(to.getY() - from.getY());
            if (maxSpeed != null) {
                move.setMaxSpeed(maxSpeed);
            }
        });
    }

    private void shiftVertical(int y) {
        delayedMoves.add(move ->
        {
            move.setAction(ActionType.MOVE);
            move.setY(y);
        });
    }

    private void scale(Point from, double factor) {
        delayedMoves.add(move ->
        {
            move.setAction(ActionType.SCALE);
            move.setX(from.getX());
            move.setY(from.getY());
            move.setFactor(factor);

        });
    }

    private void scaleHorizontal(Point from, double factor) {
        delayedMoves.add(move ->
        {
            move.setAction(ActionType.SCALE);
            move.setX(from.getX());
            move.setY(world.getHeight());
            move.setFactor(factor);
            move.setMaxSpeed(game.getHelicopterSpeed() * 0.6);
        });
    }

    private void scaleVertical(Point from, double factor) {
        delayedMoves.add(move ->
        {
            move.setAction(ActionType.SCALE);
            move.setY(from.getY());
            move.setX(world.getWidth());
            move.setFactor(factor);
            move.setMaxSpeed(game.getHelicopterSpeed() * 0.6);
        });
    }

    private void nuclearEnemyAttack(Point p, int group) {
        delayedMoves.add(move ->
        {
            move.setAction(ActionType.SCALE);
            move.setX(p.getX());
            move.setY(p.getY());
            move.setFactor(10);
            nuclearScale = true;
            battleMovingGroup = group;
        });
        delayedMoves.add(move ->
        {
            move.setAction(ActionType.SCALE);
            move.setX(p.getX());
            move.setY(p.getY());
            move.setFactor(0.1);
            nuclearDiscale = true;
            battleMovingGroup = group;
        });
    }

    private void rotateAround(Point p, double angle, double factor, int group) {
        delayedMoves.add(move ->
        {
            move.setAction(ActionType.SCALE);
            move.setX(p.getX());
            move.setY(p.getY());
            move.setFactor(factor);
            battleScaling = true;
            battleMovingGroup = group;
        });
        delayedMoves.add(move ->
        {
            move.setAction(ActionType.ROTATE);
            move.setX(p.getX());
            move.setY(p.getY());
            move.setAngle(angle);
            battleRotating = true;
            battleMovingGroup = group;
        });
        delayedMoves.add(move ->
        {
            move.setAction(ActionType.SCALE);
            move.setX(p.getX());
            move.setY(p.getY());
            move.setFactor(1 / factor);
            battleDiscaling = true;
            battleMovingGroup = group;
        });
    }

    private double getDistance(Point p1, Point p2) {
        return Math.sqrt((p1.getX() - p2.getX()) * (p1.getX() - p2.getX()) + (p1.getY() - p2.getY()) * (p1.getY() - p2.getY()));
    }

    private void selectVehicleType(VehicleType vehicleType) {
        delayedMoves.add(move ->
        {
            move.setAction(ActionType.CLEAR_AND_SELECT);
            move.setRight(world.getWidth());
            move.setBottom(world.getHeight());
            if (vehicleType != null) {
                move.setVehicleType(vehicleType);
            } else {
                selectedGroup = 0;
            }
        });
    }

    private void select(double left, double right, double top, double bottom) {
        delayedMoves.add(move ->
        {
            move.setAction(ActionType.CLEAR_AND_SELECT);
            move.setRight(right);
            move.setLeft(left);
            move.setBottom(bottom);
            move.setTop(top);
        });
    }

    private double getAngleToEnemy(int group, Point p) {
        Point mass = group == AIR ? airMass : groundMass;
        Point attack = new Point(p.x - mass.x, p.y - mass.y);
        return getAngle(group == AIR ? airVector : groundVector, attack);
    }

    private double getAngle(Point a, Point b) {
        int sign = a.x * b.y - b.x * a.y > 0 ? 1 : -1;
        return sign * Math.acos((a.x * b.x + a.y * b.y) / (Math.sqrt(a.x * a.x + a.y * a.y) * Math.sqrt(b.x * b.x + b.y * b.y)));
    }

    private Point turnVector(Point v, double ang) {
        return new Point((v.x * Math.cos(ang) - v.y * Math.sin(ang)), (v.x * Math.sin(ang) + v.y * Math.cos(ang)));
    }

    private double distance(Point p1, Point p2) {
        return Math.sqrt((p1.getX() - p2.getX()) * (p1.getX() - p2.getX()) + (p1.getY() - p2.getY()) * (p1.getY() - p2.getY()));
    }

    private void sparta(int group) {
        scale(group == AIR ? airMass : groundMass, 0.2);
    }

    private void moveVector(Point p, double maxSpeed) {
        delayedMoves.add(move ->
        {
            move.setAction(ActionType.MOVE);
            move.setX(p.x);
            move.setY(p.y);
            move.setMaxSpeed(maxSpeed);
        });
    }

    private void selectGroup(int group) {
        if (group != selectedGroup) {
            delayedMoves.add(move ->
            {
                move.setAction(ActionType.CLEAR_AND_SELECT);
                move.setRight(world.getWidth());
                move.setBottom(world.getHeight());
                move.setGroup(group);
                selectedGroup = group;
            });
        }
    }

    /**
     * Основная логика нашей стратегии.
     */
    private void go() {
        if (fireInTheHole(world.getMyPlayer())) {
            return;
        }
        if (world.getTickIndex() % ping == 0) {

            if (selectedGroup == AIR) {
                goAir();
                selectGroup(GROUND);
                goGround();
            } else {
                if (selectedGroup == GROUND) {
                    goGround();
                    selectGroup(AIR);
                    goAir();
                } else {
                    selectGroup(GROUND);
                    goGround();
                    selectGroup(AIR);
                    goAir();
                }
            }

        }
    }

    private void goGround() {
        double angleToTurn = getAngleToEnemy(GROUND, nearestGroundEnemy);
        if (Math.abs(angleToTurn) > Math.PI / 18 && distance(groundMass, nearestGroundEnemy) > 50 && isBigTarget(nearestGroundEnemy)) {
            rotateAround(groundMass, angleToTurn, factor, GROUND);
            groundVector = turnVector(groundVector, angleToTurn);
            return;
        }
        if (inBattle(GROUND)) {
            sparta(GROUND);
        } else {
            if (getGroupAvarageHealth(GROUND) > 0.2) {
                moveFromTo(groundMass, groundPoint2, game.getTankSpeed() * 0.6);
            } else {
                moveFromTo(groundMass, nearestGroundEnemy, game.getTankSpeed() * 0.6);
            }
        }
    }

    private void goAir() {

        if (distance(groundMass, nearestGroundEnemy) < 100 && isBigTarget(nearestGroundEnemy)) {
            if (distance(groundMass, airMass) < 50) {
                double angleToTurn = getAngle(airVector, groundVector);

                if (Math.abs(angleToTurn) > Math.PI / 18) {
                    rotateAround(airMass, angleToTurn, factor, AIR);
                    airVector = turnVector(airVector, angleToTurn);
                    return;
                }
            }
            moveFromTo(airMass, groundMass, game.getHelicopterSpeed() * 0.6);
        } else {
            if (!isBigTarget(nearestAirEnemy)) {
                moveFromTo(airMass, nearestAirEnemy, game.getHelicopterSpeed() * 0.6);
            } else {
                double dist = distance(groundMass, nearestAirEnemy);
                double coeff;
                if (me.getRemainingNuclearStrikeCooldownTicks() > 100) {
                    coeff = 1.2;
                } else {
                    coeff = 0.6;
                }
                Point target = new Point(groundMass.getX() + (nearestAirEnemy.getX() - groundMass.getX()) / dist * (dist - game.getFighterVisionRange() * coeff - game.getHelicopterSpeed() * 0.6 * game.getTacticalNuclearStrikeDelay()),
                        groundMass.getY() + (nearestAirEnemy.getY() - groundMass.getY()) / dist * (dist - game.getFighterVisionRange() * coeff - game.getHelicopterSpeed() * 0.6 * game.getTacticalNuclearStrikeDelay()));

                double angleToTurn = getAngleToEnemy(AIR, nearestAirEnemy);

                if (Math.abs(angleToTurn) > Math.PI / 18 && distance(airMass, nearestAirEnemy) > 30) {
                    rotateAround(airMass, angleToTurn, factor, AIR);
                    airVector = turnVector(airVector, angleToTurn);
                    return;
                }

                moveFromTo(new Point(airMass.getX(), airMass.getY()),
                        target,
                        game.getHelicopterSpeed() * 0.6);
            }
        }
    }

    private boolean isBigTarget(Point p) {
        int countEnemy = 0;
        List<Vehicle> vList = streamVehicles(Ownership.ENEMY).collect(Collectors.toList());
        for (Vehicle v : vList) {
            if (v.getDistanceTo(p.x, p.y) < game.getTacticalNuclearStrikeRadius()) {
                countEnemy++;
            }
            if (countEnemy > 30) {
                return true;
            }
        }
        return false;
    }

    private double getGroupAvarageHealth(int group) {
        double durability = 0;
        List<Vehicle> vList = streamVehicles(Ownership.ALLY).filter((v) -> v.getGroups()[0] == group).collect(Collectors.toList());
        for (Vehicle v : vList) {
            if (v.getDurability() < 70) {
                durability++;
            }
        }
        return durability / vList.size();
    }

    private boolean nuclearAttack() {
        if (me.getRemainingNuclearStrikeCooldownTicks() > 0) {
            return false;
        }
        if (distance(nearestAirEnemy, airMass) > game.getFighterVisionRange() + 100 && distance(nearestGroundEnemy, groundMass) > game.getTankVisionRange() + 100) {
            return false;
        }
        Point nuclearPoint = null;
        Vehicle nuclearAlly = null;
        Map<Point, Integer> groupCount = new HashMap<>();
        List<Vehicle> nuclearPoints = new ArrayList<>();
        for (Vehicle v : streamVehicles(Ownership.ENEMY).collect(Collectors.toList())) {
            Point p = new Point(v.getX(), v.getY());
            if (airMass != null && groundMass != null) {
                if (distance(p, airMass) > game.getFighterVisionRange() + 100 && distance(p, groundMass) > game.getTankVisionRange() + 100) {
                    continue;
                }
            }
            nuclearPoints.add(v);
            int enemyCountAround = 1;
            for (Vehicle v2 : streamVehicles(Ownership.ENEMY).collect(Collectors.toList())) {
                if (v2.getDistanceTo(p.x, p.y) < game.getTacticalNuclearStrikeRadius()) {
                    enemyCountAround++;
                }
            }
            groupCount.put(p, enemyCountAround);
        }

        nuclearPoints.sort((o1, o2) ->
        {
            Point p = new Point(o1.getX(), o1.getY());
            Point p2 = new Point(o2.getX(), o2.getY());
            return -Integer.compare(groupCount.get(p), groupCount.get(p2));
        });

        for (Vehicle enemy : nuclearPoints) {
            boolean o = false;
            int minValue = 99999;
            Point p = new Point(enemy.getX(), enemy.getY());
            for (Vehicle ally : streamVehicles(Ownership.ALLY).collect(Collectors.toList())) {
                if (isVisible(ally, p)
                        && groupCount.get(p) > 5
                        && ally.getDistanceTo(p.x, p.y) < minValue
                        && distance(p, groundMass) > game.getTacticalNuclearStrikeRadius()
                        && distance(p, airMass) > game.getTacticalNuclearStrikeRadius()) {
                    nuclearPoint = p;
                    nuclearAlly = ally;
                    o = true;
                }
            }
            if (o) {
                break;
            }
        }
        if (nuclearPoint == null) {
            return false;
        }
        final Point finalNuclearPoint = nuclearPoint;
        final Vehicle finalNuclearAlly = nuclearAlly;
        delayedMoves.add(move ->
        {
            if (isVisible(vehicleById.get(finalNuclearAlly.getId()), new Point(finalNuclearPoint.x,finalNuclearPoint.y))) {
                move.setAction(ActionType.TACTICAL_NUCLEAR_STRIKE);
                move.setX(finalNuclearPoint.x);
                move.setY(finalNuclearPoint.y);
                System.out.println("boom");
                move.setVehicleId(finalNuclearAlly.getId());
            }
        });
        return true;
    }

    private boolean isVisible(Vehicle v, Point p) {
        double dist = v.getDistanceTo(p.x, p.y);
        double coeff = 1;
        int len = weatherTypeByCellXY.length;
        if (v.isAerial()) {
            if (weatherTypeByCellXY[(int) v.getX() / len][(int) v.getY() / len] == WeatherType.RAIN) {
                coeff *= game.getRainWeatherVisionFactor();
            }
            if (weatherTypeByCellXY[(int) v.getX() / len][(int) v.getY() / len] == WeatherType.CLOUD) {
                coeff *= game.getCloudWeatherVisionFactor();
            }
        } else {
            if (terrainTypeByCellXY[(int) v.getX() / len][(int) v.getY() / len] == TerrainType.FOREST) {
                coeff *= game.getForestTerrainVisionFactor();
            }
            if (terrainTypeByCellXY[(int) v.getX() / len][(int) v.getY() / len] == TerrainType.SWAMP) {
                coeff *= game.getSwampTerrainVisionFactor();
            }
        }

        return dist < coeff * v.getVisionRange() - 1;
    }

    private boolean inBattle(int group) {
        final double[] distance = {99999};
        streamVehicles(Ownership.ALLY).filter((v) -> v.getGroups()[0] == group).forEach((x) ->
                streamVehicles(Ownership.ENEMY).forEach((y) ->
                {
                    double dist = x.getDistanceTo(y);
                    if (distance[0] > dist) {
                        distance[0] = dist;
                    }
                }));
        return distance[0] < 11;
    }

    private boolean canHit(VehicleType t1, VehicleType t2) {
        switch (t1) {
            case FIGHTER:
                return t2 == FIGHTER || t2 == HELICOPTER;
            case TANK:
            case IFV:
            case HELICOPTER:
                return true;
            default:
                return false;
        }
    }

    private Point getMassOfVehicle(Ownership ownership, VehicleType... vehicleTypes) {
        double x = streamVehicles(
                ownership, vehicleTypes
        ).mapToDouble(Vehicle::getX).average().orElse(0d);

        double y = streamVehicles(
                ownership, vehicleTypes
        ).mapToDouble(Vehicle::getY).average().orElse(0d);
        return new Point((int) x, (int) y);
    }

    private Stream<Vehicle> streamVehicles(Ownership ownership, VehicleType... vehicleTypes) {
        Predicate<Vehicle> predicate = (vehicle) ->
        {
            if (ownership == Ownership.ALLY && vehicle.getPlayerId() != me.getId()) {
                return false;
            }
            if (ownership == Ownership.ENEMY && vehicle.getPlayerId() == me.getId()) {
                return false;
            }
            if (vehicleTypes != null && vehicleTypes.length > 0 && vehicleTypes[0] != null) {
                boolean o = false;
                for (VehicleType t : vehicleTypes) {
                    if (vehicle.getType() == t) {
                        o = true;
                    }
                }
                if (!o) {
                    return false;
                }
            }
            return true;
        };

        ArrayList<Vehicle> list = new ArrayList<>();
        for (Vehicle vehicle : vehicleById.values()) {
            if (predicate.test(vehicle)) {
                list.add(vehicle);
            }
        }

        return list.stream();
    }

    private Stream<Vehicle> streamVehicles(Ownership ownership) {
        return streamVehicles(ownership, (VehicleType) null);
    }

    private Stream<Vehicle> streamVehicles() {
        return streamVehicles(Ownership.ANY);
    }

    private enum Ownership {
        ANY,

        ALLY,

        ENEMY
    }

    private class Point {
        public double x, y;

        Point(double xT, double yT) {
            x = xT;
            y = yT;
        }

        double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        @Override
        public String toString() {
            return "Point{" +
                    "x=" + x +
                    ", y=" + y +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Point point = (Point) o;

            if (Double.compare(point.x, x) != 0) {
                return false;
            }
            return Double.compare(point.y, y) == 0;
        }

        @Override
        public int hashCode() {
            int result;
            long temp;
            temp = Double.doubleToLongBits(x);
            result = (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(y);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            return result;
        }
    }
}
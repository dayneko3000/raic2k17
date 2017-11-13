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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static model.VehicleType.ARRV;
import static model.VehicleType.FIGHTER;
import static model.VehicleType.HELICOPTER;
import static model.VehicleType.IFV;
import static model.VehicleType.TANK;

@SuppressWarnings({"UnsecureRandomNumberGeneration", "FieldCanBeLocal", "unused", "OverlyLongMethod"})
public final class MyStrategy implements Strategy {
    private static final int AIR = 1;
    private final int shiftConstant = 6;
    private final Map<Long, Vehicle> vehicleById = new HashMap<>();
    private final Map<Long, Integer> updateTickByVehicleId = new HashMap<>();
    private int orderY = 100;
    private Random random;
    private TerrainType[][] terrainTypeByCellXY;
    private WeatherType[][] weatherTypeByCellXY;
    private Player me;
    private World world;
    private Game game;
    private Move move;
    private Queue<Consumer<Move>> delayedMoves = new ArrayDeque<>();

    private int ping = 50;
    private Point selfVector;
    private Point tankMass, arrvMass, helicopterMass, fighterMass, ifvMass, nearestEnemy, groundMass, airMass;
    private int center = 100;
    private Point airPoint1 = new Point(orderY, orderY), airPoint2 = new Point(200, orderY);
    private Point groundPoint1 = new Point(40, orderY), groundPoint2 = new Point((int) 120, (int) orderY), groundPoint3 = new Point(190, orderY);

    private boolean orderedHorizontal = false;
    private boolean orderedVertical = false;
    private boolean scaled = false;
    private boolean assigned = false;
    private boolean shifted = false;
    private boolean horizontaled = false;
    private boolean discaled = false;
    private boolean turned = false;
    private boolean rotating = false;
    private boolean init = false;

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
        if (rotating)
            if (enough())
                rotating = false;
            else
                return;

        if (executeDelayedMove()) {
            return;
        }
        ready();
        if (turned && enough()) {
            if (!inBattle()) {
                go();
            } else {
                sparta();
            }
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
                if (vehicleUpdate.getX() != vehicle.getX() || vehicleUpdate.getY() != vehicle.getY()) {
                    vehicleById.put(vehicleId, new Vehicle(vehicleById.get(vehicleId), vehicleUpdate));
                    updateTickByVehicleId.put(vehicleId, world.getTickIndex());
                }
            }
        }

        tankMass = getMassOfVehicle(TANK);
        helicopterMass = getMassOfVehicle(HELICOPTER);
        ifvMass = getMassOfVehicle(IFV);
        fighterMass = getMassOfVehicle(FIGHTER);
        arrvMass = getMassOfVehicle(ARRV);
        groundMass = getMassOfVehicle(ARRV, TANK, IFV);
        airMass = getMassOfVehicle(HELICOPTER, FIGHTER);

        if (!init) {
            if (tankMass.getX() > world.getWidth() / 2) {
                orderY = (int) world.getHeight() - 100;
                airPoint1 = new Point(orderY, orderY);
                airPoint2 = new Point(world.getWidth() - 200, orderY);
                groundPoint1 = new Point(world.getWidth() - 40, orderY);
                groundPoint2 = new Point(world.getWidth() - 120, orderY);
                groundPoint3 = new Point(world.getWidth() - 190, orderY);

            }
            selfVector = new Point(world.getWidth() / 2 - groundPoint2.x, world.getHeight() / 2 - groundPoint2.y);
            init = true;
        }

        final double[] distance = {99999};
        streamVehicles(
                Ownership.ENEMY, null
        ).forEach((x) ->
        {
            if (distance[0] > distance(arrvMass, new Point((int) x.getX(), (int) x.getY()))) {
                distance[0] = distance(arrvMass, new Point((int) x.getX(), (int) x.getY()));
                nearestEnemy = new Point((int) x.getX(), (int) x.getY());
            }
        });
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

    private boolean enough() {
        return streamVehicles(Ownership.ALLY).allMatch(
                vehicle -> world.getTickIndex() - updateTickByVehicleId.get(vehicle.getId()) > 2
        );
    }

    private void ready() {
        if (!orderedHorizontal) {
            double air1 = Math.abs(airPoint1.x - helicopterMass.x) + Math.abs(airPoint2.x - fighterMass.x);
            double air2 = Math.abs(airPoint2.x - helicopterMass.x) + Math.abs(airPoint1.x - fighterMass.x);
            double ground1 = Math.abs(groundPoint1.x - tankMass.x) / (game.getTankSpeed() * 0.6) + Math.abs(groundPoint2.x - ifvMass.x) + Math.abs(groundPoint3.x - arrvMass.x);
            double ground2 = Math.abs(groundPoint1.x - tankMass.x) / (game.getTankSpeed() * 0.6) + Math.abs(groundPoint3.x - ifvMass.x) + Math.abs(groundPoint2.x - arrvMass.x);
            double ground3 = Math.abs(groundPoint2.x - tankMass.x) / (game.getTankSpeed() * 0.6) + Math.abs(groundPoint1.x - ifvMass.x) + Math.abs(groundPoint3.x - arrvMass.x);
            double ground4 = Math.abs(groundPoint2.x - tankMass.x) / (game.getTankSpeed() * 0.6) + Math.abs(groundPoint3.x - ifvMass.x) + Math.abs(groundPoint1.x - arrvMass.x);
            double ground5 = Math.abs(groundPoint3.x - tankMass.x) / (game.getTankSpeed() * 0.6) + Math.abs(groundPoint1.x - ifvMass.x) + Math.abs(groundPoint2.x - arrvMass.x);
            double ground6 = Math.abs(groundPoint3.x - tankMass.x) / (game.getTankSpeed() * 0.6) + Math.abs(groundPoint2.x - ifvMass.x) + Math.abs(groundPoint1.x - arrvMass.x);
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
        if (!orderedVertical && enough()) {
            selectVehicleType(FIGHTER);
            moveVertical(fighterMass, airPoint1, game.getFighterSpeed() * 0.6);
            selectVehicleType(HELICOPTER);
            moveVertical(helicopterMass, airPoint2, game.getHelicopterSpeed() * 0.6);
            verticalGroundMove(groundPoint1, groundPoint2, groundPoint3);
            orderedVertical = true;
            return;
        }
        if (!assigned && enough()) {
            select(0, world.getWidth(), 0, orderY);
            assign(0);
            select(0, world.getWidth(), orderY, world.getHeight());
            assign(1);
            assigned = true;
            return;
        }
        if (!scaled && enough()) {
            for (double row : getRowList()) {
                selectRow((int) row);
                shiftVertical(((int) row - 100) * 2);
            }
            scaled = true;
            return;
        }
        if (!shifted && enough()) {
            selectVehicleType(TANK);
            shiftVertical(5);
            selectVehicleType(IFV);
            shiftVertical(-5);
            selectVehicleType(FIGHTER);
            shiftVertical(-5);
            shifted = true;
            return;
        }
        if (enough() && !horizontaled) {
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
        if (enough() && !turned) {
            selectVehicleType(null);
            rotateAround(groundPoint2, Math.PI / 4);
            turned = true;
            return;
        }
        if (enough() && !discaled) {
            selectVehicleType(null);
            scale(groundPoint2, 0.3, game.getTankSpeed() * 0.6);
            discaled = true;
        }
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

    private double getAngle() {
        double a = streamVehicles(Ownership.ALLY, TANK, ARRV, IFV).mapToDouble((vehicle) ->
                (vehicle.getX() - groundMass.x) * (vehicle.getY() - groundMass.y)
        ).sum();
        double b = streamVehicles(Ownership.ALLY, TANK, ARRV, IFV).mapToDouble((vehicle) ->
                (vehicle.getX() - groundMass.x) * (vehicle.getX() - groundMass.x)
        ).sum();
        return -1 / (a / b);
//        double a = streamVehicles(Ownership.ALLY, TANK, ARRV, IFV).mapToDouble(Vehicle::getX
//        ).min().orElse(0);
//        double b = streamVehicles(Ownership.ALLY, TANK, ARRV, IFV).mapToDouble(Vehicle::getY
//        ).min().orElse(0);
//        double k = streamVehicles(Ownership.ALLY, TANK, ARRV, IFV).
//                mapToDouble((vehicle -> (vehicle.getY() - b + 1) / (vehicle.getX() - a + 1))).sum();
//        return k / streamVehicles(Ownership.ALLY, TANK, ARRV, IFV).count();
    }

    private Point getGroupMass(int group) {
        double x = streamVehicles(
                Ownership.ALLY, null
        ).filter((vehicle) -> vehicle.getGroups()[0] == group).mapToDouble(Vehicle::getX).average().orElse(0d);

        double y = streamVehicles(
                Ownership.ALLY, null
        ).filter((vehicle) -> vehicle.getGroups()[0] == group).mapToDouble(Vehicle::getY).average().orElse(0d);
        return new Point((int) x, (int) y);
    }

    private void assign(int group) {
        delayedMoves.add((move) ->
        {
            move.setAction(ActionType.ASSIGN);
            move.setGroup(group);
        });
    }

    private List<Double> getRowList() {
        Set<Double> result = streamVehicles(Ownership.ALLY, VehicleType.ARRV).map(Vehicle::getY).collect(Collectors.toSet());
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

    private void scale(Point from, double factor, Double maxSpeed) {
        delayedMoves.add(move ->
        {
            move.setAction(ActionType.SCALE);
            move.setX(from.getX());
            move.setY(from.getY());
            move.setFactor(factor);
            if (maxSpeed != null) {
                move.setMaxSpeed(maxSpeed);
            }
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


    private void rotateAround(Point p, double angle) {
        delayedMoves.add(move ->
        {
            move.setAction(ActionType.ROTATE);
            move.setX(p.getX());
            move.setY(p.getY());
            move.setAngle(angle);
//            move.setMaxSpeed(game.getTankSpeed());
        });
    }

    private double getDistance(Point p1, Point p2) {
        return Math.sqrt((p1.getX() - p2.getX()) * (p1.getX() - p2.getX()) + (p1.getY() - p2.getY()) * (p1.getY() - p2.getY()));
    }

    private void setAirGroup() {
        delayedMoves.add(move ->
        {
            move.setAction(ActionType.ASSIGN);
            move.setGroup(AIR);
        });
    }

    private void selectVehicleType(VehicleType vehicleType) {
        delayedMoves.add(move ->
        {
            move.setAction(ActionType.CLEAR_AND_SELECT);
            move.setRight(world.getWidth());
            move.setBottom(world.getHeight());
            if (vehicleType != null) {
                move.setVehicleType(vehicleType);
            }
        });
    }

    private void select(double left, double right, double top, double bottom) {
        delayedMoves.add(move ->
        {
            move.setAction(ActionType.SCALE);
            move.setRight(right);
            move.setLeft(left);
            move.setBottom(bottom);
            move.setTop(top);
        });
    }

    private void selectLeft(Point core) {
        select(0, core.x, 0, (int) world.getHeight());
    }

    private void selectRight(Point core) {
        select(core.x, (int) world.getWidth(), 0, (int) world.getHeight());
    }

    private void selectTop(Point core) {
        select(0, (int) world.getWidth(), 0, core.y);
    }

    private void selectBottom(Point core) {
        select(0, (int) world.getWidth(), core.y, (int) world.getHeight());
    }

    private void set() {
        if (world.getTickIndex() % ping == 0) {
            double arrDist = distance(arrvMass, nearestEnemy);
            if (arrDist < distance(tankMass, nearestEnemy) || arrDist < distance(ifvMass, nearestEnemy)) {
                selectVehicleType(null);
                rotateAround(groundMass, Math.PI);
            }
        }
    }

    private double getAngleToEnemy() {
        Point attack = new Point(nearestEnemy.x - groundMass.x, nearestEnemy.y - groundMass.y);
//        double k = getAngle();
//        Point self = new Point(1, k);
        double angle = getAngle(selfVector, attack);
        if (Math.abs(angle) < Math.PI / 2)
            return angle;
        else if (angle < 0)
            return Math.PI + angle;
        else
            return Math.PI - angle;
    }

    private double getAngle(Point a, Point b) {
        return Math.acos((a.x * b.x + a.y * b.y) / (Math.sqrt(a.x * a.x + a.y * a.y) * Math.sqrt(b.x * b.x + b.y * b.y)));
    }

    private Point turnVector(Point v, double ang) {
        return new Point((int) (v.x * Math.cos(ang) - v.y * Math.sin(ang)), (int) (v.x * Math.sin(ang) + v.y * Math.cos(ang)));
    }

    private double distance(Point p1, Point p2) {
        return Math.sqrt((p1.getX() - p2.getX()) * (p1.getX() - p2.getX()) + (p1.getY() - p2.getY()) * (p1.getY() - p2.getY()));
    }

    private void sparta() {
        if (world.getTickIndex() % ping == 0) {
//            selectVehicleType(null);
//            scale(groundMass, 0.8, null);
        }
    }

    private void selectGroup(int group) {
        delayedMoves.add(move ->
        {
            move.setAction(ActionType.CLEAR_AND_SELECT);
            move.setRight(world.getWidth());
            move.setBottom(world.getHeight());
            move.setGroup(group);
        });
    }

    /**
     * Основная логика нашей стратегии.
     */
    private void go() {
        if (world.getTickIndex() % ping == 0 && enough()) {
            selectVehicleType(null);
            double angleToTurn = getAngleToEnemy();
            if (Math.abs(angleToTurn) > Math.PI / 50) {
                rotateAround(groundMass, angleToTurn);
                selfVector = turnVector(selfVector, angleToTurn);
                rotating = true;
                return;
            }
            Point selfCenter = getMassOfVehicle(null);
            moveFromTo(selfCenter, nearestEnemy, game.getTankSpeed() * 0.6);
        }
    }

    private void airAttack() {
//        moveFromTo(airMass, new Point(airMass));
    }

    private boolean inBattle() {
        final double[] distance = {99999};
        streamVehicles(Ownership.ALLY, null).forEach((x) ->
                streamVehicles(Ownership.ENEMY, null).forEach((y) ->
                {
                    if (canHit(x.getType(), y.getType())) {
                        distance[0] = Math.min(distance[0], Math.sqrt((x.getX() - y.getX()) * (x.getX() - y.getX()) + (x.getY() - y.getY()) * (x.getY() - y.getY())));
                    }
                }));
        if (distance[0] < 10.8) {
            return true;
        }
        return false;
    }

    private boolean canHit(VehicleType t1, VehicleType t2) {
        switch (t1) {
            case FIGHTER:
                return t2 == FIGHTER || t2 == HELICOPTER;
            case TANK:
                return t2 == IFV || t2 == ARRV;
            case IFV:
            case HELICOPTER:
                return true;
            default:
                return false;
        }
    }

    private Point getMassOfVehicle(VehicleType... vehicleTypes) {
        double x = streamVehicles(
                Ownership.ALLY, vehicleTypes
        ).mapToDouble(Vehicle::getX).average().orElse(0d);

        double y = streamVehicles(
                Ownership.ALLY, vehicleTypes
        ).mapToDouble(Vehicle::getY).average().orElse(0d);
        return new Point((int) x, (int) y);
    }

    private Stream<Vehicle> streamVehicles(Ownership ownership, VehicleType... vehicleTypes) {
        Stream<Vehicle> stream = vehicleById.values().stream();

        switch (ownership) {
            case ALLY:
                stream = stream.filter(vehicle -> vehicle.getPlayerId() == me.getId());
                break;
            case ENEMY:
                stream = stream.filter(vehicle -> vehicle.getPlayerId() != me.getId());
                break;
            default:
        }

        if (vehicleTypes != null) {
            stream = stream.filter(vehicle ->
            {
                for (VehicleType t : vehicleTypes) {
                    if (vehicle.getType() == t) {
                        return true;
                    }
                }
                return false;
            });
        }

        return stream;
    }

    private Stream<Vehicle> streamVehicles(Ownership ownership) {
        return streamVehicles(ownership, null);
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
    }
}


# SentinelAI-FSM-Game

A Java 2D game simulation demonstrating enemy AI behavior using a Finite State Machine (FSM).

## ?? Game Controls
* **W / Up Arrow:** Move Player Up
* **S / Down Arrow:** Move Player Down
* **A / Left Arrow:** Move Player Left
* **D / Right Arrow:** Move Player Right

## ?? Enemy FSM States
The red enemy AI dynamically switches behaviors based on its distance to your blue player square:
1. **PATROL:** Walks back and forth along a calm, fixed path when you are far away.
2. **CHASE:** Spots you if you enter its visual range circle and tracks you down aggressively.
3. **ATTACK:** Pauses and executes an attack routine when it catches up and touches your square.
4. **SEARCH:** Searches your last known position if you manage to quickly sprint away and break its line of sight.

## ?? How to Run the App
Double-click the SmartEnemyGame.jar file inside your desktop environment to launch and test the standalone application.

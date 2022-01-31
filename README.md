# A Decidable Method for Verifying Strategic Properties in Multi-agent Systems with Imperfect Information


## What you need to install

- MCMAS: https://vas.doc.ic.ac.uk/software/mcmas/

In the following, <path-to-mcmas> is the path to MCMAS main folder (where mcmas executable is).

## How to run (perfect information)

To check the model corresponding to the combination of Figure 1, applying Algorithm 2 and 4
```shell script
-$ java -jar StrategyRV-1.0-SNAPSHOT-jar-with-dependencies.jar -mcmas <path-to-mcmas> -m ./roverEx.json -I
```
The result should be: True (the model satisfies the formula)

The same, but changing the OR operator with an AND operator inside the formula
```shell script
-$ java -jar StrategyRV-1.0-SNAPSHOT-jar-with-dependencies.jar -mcmas <path-to-mcmas> -m ./roverExWrong.json -I
```
The result should be: False (the model does not satisfy the formula)

## How to run (imperfect recall)

To check the model corresponding to the combination of Figure 1, applying Algorithm 3 and 4
```shell script
-$ java -jar StrategyRV-1.0-SNAPSHOT-jar-with-dependencies.jar -mcmas <path-to-mcmas> -m ./roverEx.json -r
```
The result should be: True (the model satisfies the formula)

The same, but changing the OR operator with an AND operator inside the formula
```shell script
-$ java -jar StrategyRV-1.0-SNAPSHOT-jar-with-dependencies.jar -mcmas <path-to-mcmas> -m ./roverExWrong.json -r
```
The result should be: False (the model does not satisfy the formula)



NB. To generate both sub-models with perfect information/imperfect recall, you can pass both -r -I parameters.

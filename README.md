# MiyoCube Binding

This binding integrates the [Miyo Smart Irrigation System](https://miyo.garden)
The integration happens through the local Miyo Cube, which acts as the central gateway for the sensors and valves. For more info see [https://miyo.garden/pages/open-hab](https://miyo.garden/pages/open-hab)

![Miyo](doc/icon.png)
![Miyo](doc/miyo-logo.png)
[https://miyo.garden](https://miyo.garden)

## Supported Things

The Cube Bridge is required as a "bridge" for the devices connected to the physical Miyo cube.
It supports all of the standard Miyo devices.

- `cube`: The `cube`-bridge acts as the bridge for all the Miyo subcomponents
- `circuit`: The `circuit`-things represent the logical irrigation circuits for your garden
- `sensor`: The `sensor`-things represent your Miyo moisture sensors
- `valve`: The `valve`-things represent your Miyo valves

## Discovery

There is currently no auto discovery support for the Miyo Cube itself implemented.
If a Cube Bridge is set up, a scan will find all the connected devices of the cube and adds them as things.

## Binding Configuration

The binding does not need any special configuration. Just the IP-Adress of the Cube is needed, when setting up the Cube Bridge.

## Thing Configuration

### `cube` Thing Configuration

Before connecting the `cube` bridge, the hardware button on the cube must be pressed so that the linking mode is activated.
Without this, the Binding won't be able to fetch an API key from the Cube.

| Name            | Type    | Description                           | Default | Required | Advanced |
|-----------------|---------|---------------------------------------|---------|----------|----------|
| IP              | text    | IP address of the Miyo Cube           | N/A     | yes      | no       |

## Channels

| Channel                   | Type                      | Read/Write | Description                                 |
|---------------------------|---------------------------|------------|---------------------------------------------|
| temperature               | Number:Temperature        | R          | Measures the temperature from the sensor.   |
| moisture                  | Number:Dimensionless      | R          | Measures soil moisture (0-100%).            |
| brightness                | Number:Illuminance        | R          | Measures ambient brightness.                |
| solar-voltage             | Number:ElectricPotential  | R          | Measures solar panel voltage.               |
| last-update               | DateTime                  | R          | Timestamp of the last update received.      |
| valve-status              | Switch                    | R          | Status of the valve (open/closed).          |
| irrigation-was-started    | Switch                    | R          | Indicates if irrigation is currently active.|
| valve-staggering          | Switch                    | RW         | Enables/disables valve staggering.          |
| automatic-mode            | Switch                    | RW         | Enables/disables automatic irrigation mode. |
| start-irrigation          | Switch                    | RW         | Starts irrigation for the circuit.          |
| stop-irrigation           | Switch                    | RW         | Stops irrigation for the circuit.           |
| duration                  | Number:Time               | RW         | Sets the irrigation duration (1-59 min).    |

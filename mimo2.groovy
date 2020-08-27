metadata {
	definition (name: "MIMO2+", namespace: "saruf", author: "Saruf Alam") {
        
        capability "Sensor"
        capability "Contact Sensor"
        capability "Voltage Measurement"
        capability "Refresh"
        capability "Switch"
        capability "Configuration"
        
        attribute "voltage2", "number"
        
        command "on"
        command "off"
        command "on2"
        command "off2"
        command "measureVolt1"
        command "measureVolt2"
        
        fingerprint deviceId: "0520", inClusters: "0x5E,0x86,0x72,0x5A,0x59,0x71,0x85,0x8E,0x73,0x25,0x31,0x70,0x60,0x98,0x7A"
    }
    preferences {
        input name:"TimePeriod", type:"number", title: "Time Period (min)", description: "time between periodic data send (minimum 30 seconds)", defaultValue: 0, displayDuringSetup: true, required: false
        
    }
}

def parse (String description) {
    def hubitat.zwave.Command cmd = zwave.parse(description)

    if (cmd) {
        zwaveEvent(cmd)
    }
}


def CalculateVoltage(ADCvalue) // used to calculate the voltage based on the collected Scaled sensor value of the multilevel sensor event
{
    def volt = (((2.396*(10**-17))*(ADCvalue**5)) - ((1.817*(10**-13))*(ADCvalue**4)) + ((5.087*(10**-10))*(ADCvalue**3)) - ((5.868*(10**-7))*(ADCvalue**2)) + ((9.967*(10**-4))*(ADCvalue)) - (1.367*(10**-2)))
	return volt
}


////////////////////////////////////////////////////
//Helper Function to access command and endpoints///
////////////////////////////////////////////////////
private endpointcmd (cmd, endpoint){    
    
    return (zwave.multiChannelV4.multiChannelCmdEncap(bitAddress: false, sourceEndPoint:0, destinationEndPoint: endpoint).encapsulate(cmd)).format()
}

////////////////////////////////////////////////////
//////////////////Turns on Relay 1//////////////////
////////////////////////////////////////////////////
def on(){
    
    log.debug "Relay 1 on"
    endpoint = 3
    cmd = zwave.basicV1.basicSet(value: 0xff)
    return (endpointcmd(cmd, endpoint))
}

////////////////////////////////////////////////////
//////////////////Turns on Relay 2//////////////////
////////////////////////////////////////////////////
def on2(){
    
   log.debug "Relay 2 on"
   endpoint = 4
   cmd = zwave.basicV1.basicSet(value: 0xff)
   return (endpointcmd(cmd, endpoint))
    
}

////////////////////////////////////////////////////
//////////////////Turns off Relay 1/////////////////
////////////////////////////////////////////////////
def off(){

   log.debug "Relay 1 off"
   endpoint = 3
   cmd = zwave.basicV1.basicSet(value: 0x00)
   return (endpointcmd(cmd, endpoint))
    
}

////////////////////////////////////////////////////
//////////////////Turns off Relay 2/////////////////
////////////////////////////////////////////////////
def off2(){
    
   log.debug "Relay 2 off"
   endpoint = 4
   cmd = zwave.basicV1.basicSet(value: 0x00)
   return (endpointcmd(cmd, endpoint))
}

////////////////////////////////////////////////////
/////////////Requests SIG1 voltage measurement//////
////////////////////////////////////////////////////
def measureVolt1(){
    
    log.debug "Reading SIG1"
    endpoint = 1
    cmd = zwave.sensorMultilevelV9.sensorMultilevelGet(sensorType: 2)
    return (endpointcmd(cmd, endpoint))
    
}

////////////////////////////////////////////////////
/////////////Requests SIG2 voltage measurement//////
////////////////////////////////////////////////////
def measureVolt2(){
          
    log.debug "Reading SIG2"
    endpoint = 2
    cmd = zwave.sensorMultilevelV9.sensorMultilevelGet(sensorType: 2)
    return (endpointcmd(cmd, endpoint))
    
}


////////////////////////////////////////////////////
//////////Configuration helper function/////////////
////////////////////////////////////////////////////
//TO DO: make it more general, to help with trigger settings too
def prepareByte(time){
    
    //time is controlled in 30 second intervals for MIMOs
    byte scaledTime = Math.round(Double.parseDouble(time)*2)
    //OR time with byte
    byte setting = 0b00000000
    setting = setting + scaledTime  
    return setting
}

////////////////////////////////////////////////////
////Does a configuration using time preference//////
////////////////////////////////////////////////////
def configure(){
   
    byte paramValue = prepareByte(TimePeriod)
    def cmds = []
    cmds.add(zwave.configurationV1.configurationSet(size:1, parameterNumber:3, scaledConfigurationValue:0x00).format())
    cmds.add(zwave.configurationV1.configurationSet(size:1, parameterNumber:9, scaledConfigurationValue:0x00).format())
    delayBetween(cmds, 200)

    log.debug "Configured to poll ${TimePeriod} minutes"
      
}

def refresh()
{
    if (logEnable) log.debug "Refresh"

    def cmds = []
    cmds.add(zwave.versionV3.versionGet().format())

    // Signal 1 configs
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 3).format())
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 4).format())
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 5).format())
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 6).format())
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 7).format())
    // Signal 2 configs
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 9).format())
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 10).format())
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 11).format())
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 13).format())
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 14).format())
    // Signal status
    cmds.add(endpointcmd(zwave.sensorMultilevelV9.sensorMultilevelGet(sensorType: 2), 1))
    cmds.add(endpointcmd(zwave.sensorMultilevelV9.sensorMultilevelGet(sensorType: 2), 2))
    // Relay status
    cmds.add(endpointcmd(zwave.switchBinaryV1.switchBinaryGet(), 3))
    cmds.add(endpointcmd(zwave.switchBinaryV1.switchBinaryGet(), 4))
    delayBetween(cmds, 200)
}

//////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////
///////////////Overloaded ZWave Event Functions///////////////
//////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////


//Get the command and then overload another function related to that command
def zwaveEvent(hubitat.zwave.commands.multichannelv4.MultiChannelCmdEncap cmd){
    
    def encapCmd = cmd.encapsulatedCommand()
    
    log.debug ("Encapsulated endpoint ${cmd.sourceEndPoint}: ${encapCmd}")
    
    if (encapCmd){
        return zwaveEvent(cmd.sourceEndPoint, encapCmd)
    }
    
    log.warn "No command found"
    return null      
}

def zwaveEvent(int endpoint, hubitat.zwave.commands.switchbinaryv2.SwitchBinaryReport cmd){
    
    log.debug ("endpoint: ${endpoint} for binary switch")
    
    return null
    
}

def zwaveEvent(int endpoint, hubitat.zwave.commands.sensormultilevelv11.SensorMultilevelReport cmd){
    
    volts = CalculateVoltage(cmd.scaledSensorValue)
    log.debug "ADC ${endpoint} has value ${cmd.scaledSensorValue}: ${volts} V"
    
}

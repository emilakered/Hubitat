/**
 *  Hank HKZW-SCN01 Device Driver for Hubitat by Emil Åkered (@emilakered)
 *  Based on SmartThings DTH "Fibaro Button", copyright 2017 Ronald Gouldner (@gouldner)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *	2018-04-07
 *	- Initial conversion to Hubitat
 *
 */
 
metadata {
    definition (name: "Hank One-key Scene Controller", namespace: "emilakered", author: "Emil Åkered") {
        capability "Battery"
        capability "PushableButton"
        capability "HoldableButton" 
        
        attribute "lastPressed", "string"
		attribute "lastSequence", "number"
		
		fingerprint mfr: "0208", prod: "0200", model: "0009"
        fingerprint deviceId: "0x1801", inClusters: "0x5E,0x86,0x72,0x5B,0x59,0x85,0x80,0x84,0x73,0x70,0x7A,0x5A", outClusters: "0x26"
    }
}

def parse(String description) {
    //log.debug ("Parsing description:$description")
    def event
    def results = []
    
    //log.debug("RAW command: $description")
    if (description.startsWith("Err")) {
        log.debug("An error has occurred")
    } else { 
        def cmd = zwave.parse(description)
        //log.debug "Parsed Command: $cmd"
        if (cmd) {
            event = zwaveEvent(cmd)
            if (event) {
                results += event
            }
        }
    }
    return results
}


def zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
        //log.debug ("SecurityMessageEncapsulation cmd:$cmd")
		//log.debug ("Secure command")
        def encapsulatedCommand = cmd.encapsulatedCommand([0x98: 1, 0x20: 1])

        if (encapsulatedCommand) {
            //log.debug ("SecurityMessageEncapsulation encapsulatedCommand:$encapsulatedCommand")
            return zwaveEvent(encapsulatedCommand)
        }
        log.debug ("No encalsulatedCommand Processed")
}

def zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpNotification cmd) {
    log.debug("Button Woke Up!")
    def event = createEvent(descriptionText: "${device.displayName} woke up", displayed: false)
    def cmds = []
    cmds += zwave.wakeUpV1.wakeUpNoMoreInformation()
    
    [event, encapSequence(cmds, 500)]
}

def zwaveEvent(hubitat.zwave.commands.centralscenev1.CentralSceneNotification cmd) {
    //log.debug( "CentralSceneNotification: $cmd")
	def now = new Date().format("yyyy MMM dd EEE HH:mm:ss", location.timeZone)	
	sendEvent(name: "lastPressed", value: now, displayed: false)
	
	if (device.currentValue("lastSequence") != cmd.sequenceNumber){

		sendEvent(name: "lastSequence", value: cmd.sequenceNumber, displayed: false)
    
		if (cmd.keyAttributes == 0) {
			sendEvent(name: "pushed", value: 1, data: [buttonNumber: 1], descriptionText: "$device.displayName button was pushed", isStateChange: true)
			log.debug( "Button pushed" )
		}
		if (cmd.keyAttributes == 2) {
			sendEvent(name: "held", value: 1, data: [buttonNumber: 1], descriptionText: "$device.displayName button was held", isStateChange: true)
			log.debug( "Button held" )
		}
	} else {
		log.debug( "Duplicate sequenceNumber dropped!")
	}
}

def zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
    log.debug("BatteryReport: $cmd")
	def val = (cmd.batteryLevel == 0xFF ? 1 : cmd.batteryLevel)
	if (val > 100) {
		val = 100
	}  	
	def isNew = (device.currentValue("battery") != val)    
	def result = []
	result << createEvent(name: "battery", value: val, unit: "%", display: isNew, isStateChange: isNew)	
	return result
}

def zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
    log.debug("V1 ConfigurationReport cmd: $cmd")
}

def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.DeviceSpecificReport cmd) {
    log.debug("DeviceSpecificReport cmd: $cmd")
}

def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
    log.debug("ManufacturerSpecificReport cmd: $cmd")
}

private encapSequence(commands, delay=200) {
        delayBetween(commands.collect{ encap(it) }, delay)
}

private secure(hubitat.zwave.Command cmd) {
        zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
}

private nonsecure(hubitat.zwave.Command cmd) {
		"5601${cmd.format()}0000"
}

private encap(hubitat.zwave.Command cmd) {
    def secureClasses = [0x5B, 0x85, 0x84, 0x5A, 0x86, 0x72, 0x71, 0x70 ,0x8E, 0x9C]
    if (secureClasses.find{ it == cmd.commandClassId }) {
        secure(cmd)
    } else {
        nonsecure(cmd)
    }
}
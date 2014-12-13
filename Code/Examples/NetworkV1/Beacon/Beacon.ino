/**
 * This file is part of the Meshwork project.
 *
 * Copyright (C) 2014, Sinisha Djukic
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General
 * Public License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 * Boston, MA  02111-1307  USA
 */
#ifndef __EXAMPLES_BEACON_H__
#define __EXAMPLES_BEACON_H__

//Note: comment this out to disable LED tracing
#define LED_TRACING

#ifdef LED_TRACING
	//Note: increase the delay factory multiplier to give more blink time for LEDs
	#define MW_DELAY_FACTOR	5
	//Enable NetworkV1::RadioListener in the code
	#define SUPPORT_RADIO_LISTENER
#endif

#include "Config.h"

#if FULL_DEBUG != false
	#define LOG_BEACON	true
#else
	#define LOG_BEACON	false
#endif

#include <stdlib.h>
#include <Cosa/Board.hh>
#include <Cosa/Trace.hh>
#include <Cosa/Types.h>
#include <Cosa/IOStream.hh>
#include <Cosa/IOStream/Driver/UART.hh>
#include <Cosa/Watchdog.hh>
#include <Cosa/RTC.hh>
#include <Cosa/Wireless.hh>

//BEGIN: Include set for initializing the network
#include <Meshwork.h>
#include <Meshwork/L3/Network.h>
#include <Meshwork/L3/NetworkV1/NetworkV1.h>
#include <Meshwork/L3/NetworkV1/NetworkV1.cpp>
#include "NetworkInit.h"
//END: Include set for initializing the network

#ifdef LED_TRACING
	OutputPin pin_send(Board::D4);
	OutputPin pin_recv(Board::D5);
	OutputPin pin_ack(Board::D6);
	#include "Utils/LEDTracing.h"
	LEDTracing ledTracing(&mesh, &pin_send, &pin_recv, &pin_ack);
#endif

static const uint16_t 	BEACON_NWK_ID 		= 1;
static const uint8_t 	BEACON_CHANNEL_ID 	= 0;
static const uint8_t 	BEACON_NODE_ID 		= 100;
static const uint8_t 	BEACON_BCAST_PORT 	= 0;
static const char 		BEACON_BCAST_MSG[] 	= "*BEACON*";
static const uint8_t	BEACON_BCAST_MSG_LEN = sizeof(BEACON_BCAST_MSG) - 1;//without null termination

void setup()
{
  uart.begin(115200);
  trace.begin(&uart, PSTR("Beacon: started\n"));
  
  MW_LOG_DEBUG_TRACE(LOG_BEACON) << PSTR("Network ID: ") << BEACON_NWK_ID << endl;
  MW_LOG_DEBUG_TRACE(LOG_BEACON) << PSTR("Channel ID: ") << BEACON_CHANNEL_ID << endl;
  MW_LOG_DEBUG_TRACE(LOG_BEACON) << PSTR("Node ID: ") << BEACON_NODE_ID << endl;
  MW_LOG_DEBUG_TRACE(LOG_BEACON) << PSTR("Bcast port: ") << BEACON_BCAST_PORT << endl;
  MW_LOG_DEBUG_TRACE(LOG_BEACON) << PSTR("Bcast msg len: ") << BEACON_BCAST_MSG_LEN << endl;
  
#ifdef LED_TRACING
  mesh.set_radio_listener(&ledTracing);
#endif

  mesh.setNetworkID(BEACON_NWK_ID);
  mesh.setChannel(BEACON_CHANNEL_ID);
  mesh.setNodeID(BEACON_NODE_ID);
  mesh.begin(NULL);
  
  uint8_t mode = SLEEP_MODE_IDLE;
  Watchdog::begin(16);  
//  rf.set_sleep(mode);
  RTC::begin();
}

void loop()
{
	MW_LOG_DEBUG_TRACE(LOG_BEACON) << PSTR("Broadcasting...") << endl;
	mesh.broadcast(BEACON_BCAST_PORT, BEACON_BCAST_MSG, BEACON_BCAST_MSG_LEN);
	Watchdog::delay(1000);
}
#endif

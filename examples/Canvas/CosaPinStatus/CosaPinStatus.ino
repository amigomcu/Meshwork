/**
 * @file CosaPinStatus.ino
 * @version 1.0
 *
 * @section License
 * Copyright (C) 2012, Mikael Patel
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
 *
 * @section Description
 * Cosa demonstration of Canvas device driver for ST7735R, 262K Color 
 * Single-Chip TFT Controller, and monitoring of Arduino pins.
 *
 * This file is part of the Arduino Che Cosa project.
 */

#include "Cosa/Watchdog.hh"
#include "Cosa/IOStream.hh"
#include "Cosa/SPI/ST7735R.hh"

ST7735R tft;
IOStream cout(&tft);

void setup()
{
  Watchdog::begin();
  tft.begin();
  tft.set_pen_color(tft.WHITE);
  tft.fill_screen();
}

void loop()
{
  for (uint8_t x = 0, y = 2; y < tft.SCREEN_HEIGHT; y += 20, x++) {
    tft.set_pen_color(tft.grayscale(75));
    tft.fill_rect(10, y, tft.SCREEN_WIDTH - 20, 16);
    tft.set_pen_color(tft.BLACK);
    tft.draw_rect(10, y, tft.SCREEN_WIDTH - 20, 16);
    tft.set_cursor(15, y + 5);
    cout.printf_P(PSTR("D%d"), x);
    tft.set_pen_color(digitalRead(x) ? tft.RED : tft.GREEN);
    tft.fill_circle(35, y + 8, 5);
    tft.set_cursor(55, y + 5);
    cout.printf_P(PSTR("A%d %d"), x, analogRead(x));
  }
  Watchdog::delay(512);  
}

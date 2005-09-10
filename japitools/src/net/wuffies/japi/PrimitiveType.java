///////////////////////////////////////////////////////////////////////////////
// Japize - Output a machine-readable description of a Java API.
// Copyright (C) 2000,2002,2003,2004,2005  Stuart Ballard <stuart.a.ballard@gmail.com>
// 
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
// 
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
// 
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
///////////////////////////////////////////////////////////////////////////////

package net.wuffies.japi;

public class PrimitiveType extends Type {
  char code;
  public PrimitiveType(char code) {
    this.code = code;
  }
  public String getTypeSig() {
    return "" + code;
  }
  static final PrimitiveType VOID = new PrimitiveType('V');
  static final PrimitiveType BOOLEAN = new PrimitiveType('Z');
  static final PrimitiveType CHAR = new PrimitiveType('C');
  static final PrimitiveType BYTE = new PrimitiveType('B');
  static final PrimitiveType SHORT = new PrimitiveType('S');
  static final PrimitiveType INT = new PrimitiveType('I');
  static final PrimitiveType LONG = new PrimitiveType('J');
  static final PrimitiveType FLOAT = new PrimitiveType('F');
  static final PrimitiveType DOUBLE = new PrimitiveType('D');
}

///////////////////////////////////////////////////////////////////////////////
// Japize - Output a machine-readable description of a Java API.
// Copyright (C) 2000-2002  Stuart Ballard <sballard@netreach.net>
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

interface CallWrapper extends Wrapper, Comparable {
  String[] getParameterTypes();
  String[] getExceptionTypes();
  String getName();
  String getReturnType();
  ClassWrapper getDeclaringClass();
  // Check for duplicates within the same Class object. This can happen in
  // Reflection when an interface inherits from more than one interface (or
  // from an interface and the Object class). In this case Reflection gives
  // both methods as results, and we only want to present the data one time.
  boolean isDup();
}


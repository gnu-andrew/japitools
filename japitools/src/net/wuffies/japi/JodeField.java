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
import jode.bytecode.*;
import java.lang.reflect.Modifier;

class JodeField implements FieldWrapper {
  private int modifiers;
  private Object constValue;
  private String name;
  private String type;
  private JodeClass jc;
  JodeField(FieldInfo f, JodeClass jc) {
    modifiers = f.getModifiers();
    constValue = f.getConstant();
    if (!Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers) ||
        (!Modifier.isPublic(modifiers) && !Modifier.isProtected(modifiers))) {
      constValue = null;
    }
    name = f.getName();
    type = f.getType();
    this.jc = jc;
  }
  public int getModifiers() {
    return modifiers;
  }
  public String getName() {
    return name;
  }
  public String getType() {
    return type;
  }
  public boolean isPrimitiveConstant() {
    return constValue != null;
  }
  public Object getPrimitiveValue() {
    return constValue;
  }
  public ClassWrapper getDeclaringClass() {
    return jc;
  }
  public boolean equals(Object o) {
    return o instanceof JodeField && ((JodeField)o).getName().equals(getName());
  }
  public int compareTo(Object o) {
    return getName().compareTo(((JodeField) o).getName());
  }
}


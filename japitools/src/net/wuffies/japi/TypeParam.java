///////////////////////////////////////////////////////////////////////////////
// Japize - Output a machine-readable description of a Java API.
// Copyright (C) 2000,2002,2003,2004  Stuart Ballard <stuart.a.ballard@gmail.com>
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
import java.lang.reflect.Modifier;

// Note that a TypeParam *must* correspond to one of the entries in its
// getAssociatedWrapper().getTypeParams() array.
public class TypeParam extends RefType {
  private GenericWrapper associatedWrapper;
  private ClassType primaryConstraint;
  public TypeParam(GenericWrapper associatedWrapper, ClassType primaryConstraint) {
    this.associatedWrapper = associatedWrapper;
    this.primaryConstraint = primaryConstraint;
  }
  // Later we'll support other constraints once we understand better what's permitted
  // and what's not.

  public GenericWrapper getAssociatedWrapper() {
    return associatedWrapper;
  }
  public ClassType getPrimaryConstraint() {
    return primaryConstraint;
  }
  public int getIndex() {
    int base = 0;
    if (getAssociatedWrapper() instanceof ClassWrapper) {
      ClassWrapper cls = (ClassWrapper) getAssociatedWrapper();
      while (cls != null && (cls.getModifiers() & Modifier.STATIC) == 0) {
        ClassWrapper container = cls.getContainingClass();
        if (container != null && container.getTypeParams() != null) {
          base += container.getTypeParams().length;
        }
        cls = container;
      }
    }
    TypeParam[] params = getAssociatedWrapper().getTypeParams();
    for (int i = 0; i < params.length; i++) {
      if (params[i] == this) return base + i;
    }
    throw new RuntimeException("TypeParam not found in associated class or method");
  }
  public String getTypeSig() {
    return "@" + getIndex();
  }
  public String getNonGenericTypeSig() {
    return getPrimaryConstraint().getNonGenericTypeSig();
  }
}

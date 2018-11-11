package clickme.nocubes.asm;

import java.util.Iterator;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class BlockTweakInjector implements IClassTransformer {
   public byte[] transform(String name, String transformedName, byte[] bytes) {
      if (bytes == null) {
         return null;
      } else {
         boolean obfuscated = true;
         if (!"ahu".equals(name)) {
            if (!"net.minecraft.block.Block".equals(name)) {
               return bytes;
            }

            obfuscated = false;
         }

         ClassNode classNode = new ClassNode();
         ClassReader classReader = new ClassReader(bytes);
         classReader.accept(classNode, 8);
         MethodNode targetMethod = null;
         Iterator i$ = classNode.methods.iterator();

         while(i$.hasNext()) {
            MethodNode methodNode = (MethodNode)i$.next();
            if (obfuscated) {
               if ("a".equals(methodNode.name) && "(Lafn;IIILayf;Ljava/util/List;Lqn;)V".equals(methodNode.desc)) {
                  targetMethod = methodNode;
                  break;
               }
            } else if ("addCollisionBoxesToList".equals(methodNode.name)) {
               targetMethod = methodNode;
               break;
            }
         }

         if (targetMethod == null) {
            return bytes;
         } else {
            MethodNode injectedMethod = new MethodNode();
            Label label0 = new Label();
            injectedMethod.visitLabel(label0);
            injectedMethod.visitVarInsn(25, 0);
            injectedMethod.visitMethodInsn(184, "clickme/nocubes/NoCubes", "isBlockSoftForCollision", obfuscated ? "(Lahu;)Z" : "(Lnet/minecraft/block/Block;)Z");
            Label label1 = new Label();
            injectedMethod.visitJumpInsn(153, label1);
            Label label2 = new Label();
            injectedMethod.visitLabel(label2);
            injectedMethod.visitVarInsn(25, 0);
            injectedMethod.visitVarInsn(25, 1);
            injectedMethod.visitVarInsn(21, 2);
            injectedMethod.visitVarInsn(21, 3);
            injectedMethod.visitVarInsn(21, 4);
            injectedMethod.visitVarInsn(25, 5);
            injectedMethod.visitVarInsn(25, 6);
            injectedMethod.visitVarInsn(25, 7);
            injectedMethod.visitMethodInsn(184, "clickme/nocubes/SoftBlockRenderer", "inject", obfuscated ? "(Lahu;Lafn;IIILayf;Ljava/util/List;Lqn;)V" : "(Lnet/minecraft/block/Block;Lnet/minecraft/world/World;IIILnet/minecraft/util/AxisAlignedBB;Ljava/util/List;Lnet/minecraft/entity/Entity;)V");
            Label label3 = new Label();
            injectedMethod.visitLabel(label3);
            injectedMethod.visitInsn(177);
            injectedMethod.visitLabel(label1);
            targetMethod.instructions.insert(injectedMethod.instructions);
            ClassWriter writer = new ClassWriter(3);
            classNode.accept(writer);
            return writer.toByteArray();
         }
      }
   }
}